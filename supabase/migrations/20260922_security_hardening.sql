-- ============================================================================
-- EgyptDealRadar - Security hardening, anti-abuse & data quality
-- 2026-09-22
--
-- Principles:
--   * The app NEVER touches tables directly. All access goes through Edge
--     Functions, which run as service_role and derive the caller from the
--     user's JWT (auth.getUser), never from the request body.
--   * anon / authenticated roles get no table access and cannot call
--     SECURITY DEFINER functions over REST.
--   * Storage: public read only; uploads use short-lived signed URLs issued
--     by an Edge Function.
-- Idempotent: safe to run more than once.
-- ============================================================================

-- ---------------------------------------------------------------------------
-- 0. Extensions
-- ---------------------------------------------------------------------------
create extension if not exists pg_trgm with schema extensions;

-- ---------------------------------------------------------------------------
-- 1. Remove every permissive policy on app tables
-- ---------------------------------------------------------------------------
drop policy if exists "Anyone can read user profiles"          on public.users;
drop policy if exists "New users can create profile"           on public.users;
drop policy if exists "Admins can update any user role"        on public.users;
drop policy if exists "Users can update own profile"           on public.users;
drop policy if exists "Public can read approved deals"         on public.deals;
drop policy if exists "Allow read of votes"                    on public.votes;
drop policy if exists "Allow read of reports"                  on public.reports;
drop policy if exists "Users can submit feedback"              on public.feedback;
drop policy if exists "Users can view own feedback"            on public.feedback;
drop policy if exists "Admins and moderators can view all feedback"   on public.feedback;
drop policy if exists "Admins and moderators can update feedback"     on public.feedback;
drop policy if exists "anon_read_only"                         on public.user_identities;
drop policy if exists "System can insert audit log"            on public.audit_log;
drop policy if exists "Only admins can read audit log"         on public.audit_log;

-- Storage: keep public READ of deal images, remove anonymous upload and
-- "any logged-in user can delete any file".
drop policy if exists "Allow anonymous uploads 1ktzlz_0"       on storage.objects;
drop policy if exists "Allow delete own files 1ktzlz_0"        on storage.objects;

-- Only JPEG/WebP, max 3 MB (app compresses to well below this)
update storage.buckets
   set file_size_limit = 3145728,
       allowed_mime_types = array['image/jpeg','image/webp']
 where id = 'deals';

-- Old Qatar-era backup table (contains old deals + device ids)
drop table if exists public.deals_backup_20251023;

-- Make sure RLS is on everywhere (deny-by-default for anon/authenticated)
alter table public.users            enable row level security;
alter table public.deals            enable row level security;
alter table public.votes            enable row level security;
alter table public.reports          enable row level security;
alter table public.feedback         enable row level security;
alter table public.user_identities  enable row level security;
alter table public.audit_log        enable row level security;

-- Defence in depth: no table privileges for client roles at all
revoke all on all tables    in schema public from anon, authenticated;
revoke all on all sequences in schema public from anon, authenticated;
alter default privileges in schema public revoke all on tables    from anon, authenticated;
alter default privileges in schema public revoke all on sequences from anon, authenticated;

-- ---------------------------------------------------------------------------
-- 2. Link app users to Supabase Auth users
-- ---------------------------------------------------------------------------
alter table public.users add column if not exists auth_user_id uuid;
alter table public.users add column if not exists strikes      integer not null default 0;
alter table public.users add column if not exists consent_at   timestamptz;
alter table public.users add column if not exists banned_at    timestamptz;

do $$
begin
  if not exists (select 1 from pg_constraint where conname = 'users_auth_user_id_fkey') then
    alter table public.users
      add constraint users_auth_user_id_fkey
      foreign key (auth_user_id) references auth.users(id) on delete set null;
  end if;
end $$;

create unique index if not exists users_auth_user_id_key on public.users (auth_user_id) where auth_user_id is not null;

-- Backfill: match existing profiles to auth users by email
update public.users u
   set auth_user_id = a.id
  from auth.users a
 where u.auth_user_id is null
   and lower(a.email) = lower(u.email);

-- ---------------------------------------------------------------------------
-- 3. Deals: fields for duplicates, location, expiry confirmations
-- ---------------------------------------------------------------------------
alter table public.deals add column if not exists canonical_url     text;
alter table public.deals add column if not exists title_norm        text;
alter table public.deals add column if not exists image_hash        bigint;
alter table public.deals add column if not exists governorate       text;
alter table public.deals add column if not exists expired_votes     integer not null default 0;
alter table public.deals add column if not exists store             text;

-- The old "unique title per posted_by" index blocks legitimate re-posts of a
-- recurring offer by the same user months later; duplicates are now handled
-- by the duplicate detector instead.
drop index if exists public.deals_unique_title_per_user;

-- Feed: approved, visible deals, hottest / newest
create index if not exists deals_feed_hot_idx
  on public.deals (hot_count desc, created_at desc, id desc)
  where status = 'approved' and is_archived = false and deleted_at is null;
create index if not exists deals_feed_new_idx
  on public.deals (created_at desc, id desc)
  where status = 'approved' and is_archived = false and deleted_at is null;
create index if not exists deals_governorate_idx on public.deals (governorate);
create index if not exists deals_canonical_url_idx on public.deals (canonical_url) where canonical_url is not null;
create index if not exists deals_title_trgm_idx on public.deals using gin (title_norm extensions.gin_trgm_ops);
create index if not exists deals_status_created_idx on public.deals (status, created_at desc);

-- ---------------------------------------------------------------------------
-- 4. Votes: account-only from now on
-- ---------------------------------------------------------------------------
-- Old anonymous (device-only) votes no longer count
delete from public.votes where user_id is null;

-- ---------------------------------------------------------------------------
-- 5. Reports: one per account per deal
-- ---------------------------------------------------------------------------
alter table public.reports add column if not exists reporter_user_id uuid references public.users(id) on delete cascade;
create unique index if not exists reports_deal_reporter_key on public.reports (deal_id, reporter_user_id) where reporter_user_id is not null;

-- ---------------------------------------------------------------------------
-- 6. "Is this deal expired?" confirmations
-- ---------------------------------------------------------------------------
create table if not exists public.deal_expiry_votes (
  deal_id    uuid not null references public.deals(id) on delete cascade,
  user_id    uuid not null references public.users(id) on delete cascade,
  created_at timestamptz not null default now(),
  primary key (deal_id, user_id)
);
alter table public.deal_expiry_votes enable row level security;
revoke all on public.deal_expiry_votes from anon, authenticated;

-- ---------------------------------------------------------------------------
-- 7. Rate limiting (fixed window counters)
-- ---------------------------------------------------------------------------
create table if not exists public.rate_limits (
  key          text        not null,
  window_start timestamptz not null,
  count        integer     not null default 0,
  primary key (key, window_start)
);
alter table public.rate_limits enable row level security;
revoke all on public.rate_limits from anon, authenticated;

-- Returns seconds to wait (0 = allowed). Increments the counter when allowed.
create or replace function public.hit_rate_limit(p_key text, p_limit integer, p_window_seconds integer)
returns integer
language plpgsql
security definer
set search_path = public
as $$
declare
  v_window timestamptz := to_timestamp(floor(extract(epoch from now()) / p_window_seconds) * p_window_seconds);
  v_count  integer;
begin
  insert into rate_limits (key, window_start, count)
  values (p_key, v_window, 1)
  on conflict (key, window_start) do update set count = rate_limits.count + 1
  returning count into v_count;

  if v_count > p_limit then
    -- undo the increment so a blocked attempt doesn't extend the block
    update rate_limits set count = count - 1 where key = p_key and window_start = v_window;
    return greatest(1, ceil(extract(epoch from (v_window + make_interval(secs => p_window_seconds) - now())))::integer);
  end if;
  return 0;
end $$;

-- ---------------------------------------------------------------------------
-- 8. Voting: toggle semantics + no voting on own deal (enforced in function)
-- ---------------------------------------------------------------------------
-- Returns the caller's resulting vote ('hot' | 'cold' | null when removed)
create or replace function public.toggle_vote(p_deal_id uuid, p_user_id uuid, p_vote_type text)
returns text
language plpgsql
security definer
set search_path = public
as $$
declare
  v_existing text;
  v_result   text;
begin
  if p_vote_type not in ('hot', 'cold') then
    raise exception 'invalid vote type';
  end if;

  select vote_type into v_existing from votes where deal_id = p_deal_id and user_id = p_user_id for update;

  if v_existing is null then
    insert into votes (deal_id, user_id, vote_type) values (p_deal_id, p_user_id, p_vote_type);
    v_result := p_vote_type;
  elsif v_existing = p_vote_type then
    delete from votes where deal_id = p_deal_id and user_id = p_user_id;   -- same vote again = remove
    v_result := null;
  else
    update votes set vote_type = p_vote_type, created_at = now() where deal_id = p_deal_id and user_id = p_user_id;
    v_result := p_vote_type;
  end if;

  update deals d
     set hot_count  = (select count(*) from votes v where v.deal_id = d.id and v.vote_type = 'hot'),
         cold_count = (select count(*) from votes v where v.deal_id = d.id and v.vote_type = 'cold')
   where d.id = p_deal_id;

  return v_result;
end $$;

-- ---------------------------------------------------------------------------
-- 9. Trust levels: ONE place that promotes users (fixes double counting:
--    approve_deal no longer increments counters itself)
--    New -> Trusted (auto-approve) after 5 approved deals with < 2 strikes.
-- ---------------------------------------------------------------------------
create or replace function public.increment_approved_on_status_change()
returns trigger
language plpgsql
security definer
set search_path = public
as $$
begin
  if new.status = 'approved'
     and (old.status is null or old.status <> 'approved')
     and new.submitted_by_user_id is not null then
    update users
       set approved_deals_count = coalesce(approved_deals_count, 0) + 1,
           trust_level = case when coalesce(approved_deals_count, 0) + 1 >= 5 and strikes < 2 then 'trusted'
                              when coalesce(approved_deals_count, 0) + 1 >= 1 then 'regular'
                              else coalesce(trust_level, 'new') end,
           auto_approve = case when coalesce(approved_deals_count, 0) + 1 >= 5 and strikes < 2 and role = 'user' then true
                               else auto_approve end
     where id = new.submitted_by_user_id;
  end if;

  if new.status = 'rejected'
     and (old.status is null or old.status <> 'rejected')
     and new.submitted_by_user_id is not null then
    update users
       set rejected_deals_count = coalesce(rejected_deals_count, 0) + 1,
           strikes = strikes + 1,
           auto_approve = case when strikes + 1 >= 2 and role = 'user' then false else auto_approve end,
           trust_level  = case when strikes + 1 >= 2 and role = 'user' then 'regular' else trust_level end
     where id = new.submitted_by_user_id;
  end if;
  return new;
end $$;

drop trigger if exists trigger_increment_approved_deals on public.deals;
create trigger trigger_increment_approved_deals
  after update on public.deals
  for each row execute function public.increment_approved_on_status_change();

-- Also count deals that were approved on insert (trusted users / moderators)
create or replace function public.count_approved_on_insert()
returns trigger
language plpgsql
security definer
set search_path = public
as $$
begin
  if new.submitted_by_user_id is not null then
    update users set submitted_deals_count = coalesce(submitted_deals_count, 0) + 1,
                     total_deals_posted    = coalesce(total_deals_posted, 0) + 1
     where id = new.submitted_by_user_id;
    if new.status = 'approved' then
      update users set approved_deals_count = coalesce(approved_deals_count, 0) + 1
       where id = new.submitted_by_user_id;
    end if;
  end if;
  return new;
end $$;

drop trigger if exists trigger_count_deal_insert on public.deals;
create trigger trigger_count_deal_insert
  after insert on public.deals
  for each row execute function public.count_approved_on_insert();

-- ---------------------------------------------------------------------------
-- 10. Duplicate detection helper
--     Returns active deals that look like the same offer.
-- ---------------------------------------------------------------------------
create or replace function public.find_similar_deals(
  p_canonical_url text,
  p_title_norm    text,
  p_image_hash    bigint,
  p_exclude_id    uuid default null
)
returns table (id uuid, title text, image_url text, match_type text, score real)
language sql
stable
security definer
set search_path = public, extensions
as $$
  with candidates as (
    select d.id, d.title, d.image_url, d.canonical_url, d.title_norm, d.image_hash
      from deals d
     where d.status in ('approved', 'pending')
       and d.is_archived = false
       and d.deleted_at is null
       and d.created_at > now() - interval '30 days'
       and (p_exclude_id is null or d.id <> p_exclude_id)
  )
  select c.id, c.title, c.image_url, 'url'::text, 1.0::real
    from candidates c
   where p_canonical_url is not null and c.canonical_url = p_canonical_url
  union all
  select c.id, c.title, c.image_url, 'image'::text, 0.95::real
    from candidates c
   where p_image_hash is not null and c.image_hash is not null
     and bit_count((c.image_hash # p_image_hash)::bit(64)) <= 6
  union all
  select c.id, c.title, c.image_url, 'title'::text, similarity(c.title_norm, p_title_norm)
    from candidates c
   where p_title_norm is not null and c.title_norm is not null
     and similarity(c.title_norm, p_title_norm) >= 0.6
  order by 5 desc
  limit 5;
$$;

-- ---------------------------------------------------------------------------
-- 11. Retention (PDPL: defined, limited retention)
-- ---------------------------------------------------------------------------
create or replace function public.purge_old_data()
returns table (deals_deleted integer, reports_deleted integer, feedback_deleted integer, image_paths text[])
language plpgsql
security definer
set search_path = public
as $$
declare
  v_deals integer; v_reports integer; v_feedback integer; v_paths text[];
begin
  -- images of deals that will be deleted (returned so the Edge Function removes them)
  select coalesce(array_agg(image_url), '{}') into v_paths
    from deals
   where (is_archived = true and coalesce(expires_at, created_at) < now() - interval '90 days')
      or (status = 'rejected' and created_at < now() - interval '30 days')
      or (deleted_at is not null and deleted_at < now() - interval '30 days');

  with del as (
    delete from deals
     where (is_archived = true and coalesce(expires_at, created_at) < now() - interval '90 days')
        or (status = 'rejected' and created_at < now() - interval '30 days')
        or (deleted_at is not null and deleted_at < now() - interval '30 days')
    returning 1)
  select count(*) into v_deals from del;

  with del as (delete from reports where created_at < now() - interval '90 days' returning 1)
  select count(*) into v_reports from del;

  with del as (delete from feedback where created_at < now() - interval '365 days' returning 1)
  select count(*) into v_feedback from del;

  delete from rate_limits where window_start < now() - interval '2 days';
  delete from audit_log where created_at < now() - interval '365 days';

  return query select v_deals, v_reports, v_feedback, v_paths;
end $$;

-- Daily maintenance: the `maintenance` Edge Function archives expired deals,
-- calls purge_old_data() and deletes the returned image files from Storage.
-- Authenticated with a random secret stored in Vault ('cron_secret') and in
-- the function's CRON_SECRET env var (set outside this file).
do $$
begin
  if exists (select 1 from cron.job where jobname = 'daily-maintenance') then
    perform cron.unschedule('daily-maintenance');
  end if;
  perform cron.schedule(
    'daily-maintenance',
    '15 0 * * *',
    $cron$
      select net.http_post(
        url     := 'https://nzchbnshkrkdqpcawohu.supabase.co/functions/v1/maintenance',
        headers := jsonb_build_object(
                     'Content-Type', 'application/json',
                     'x-cron-secret', (select decrypted_secret from vault.decrypted_secrets where name = 'cron_secret')),
        body    := '{}'::jsonb
      );
    $cron$
  );
end $$;

-- ---------------------------------------------------------------------------
-- 12. No function is callable by client roles over REST
-- ---------------------------------------------------------------------------
revoke execute on all functions in schema public from public, anon, authenticated;
grant  execute on all functions in schema public to service_role;
alter default privileges in schema public revoke execute on functions from public, anon, authenticated;
alter default privileges in schema public grant  execute on functions to service_role;
