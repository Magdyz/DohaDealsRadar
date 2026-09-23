-- ============================================================================
-- Scale tier 1 (2026-09-24)
--   1. Index: deals by poster (Account / profile / account deletion)
--   2. Trigram indexes so feed search (title / description ILIKE) uses indexes
--   3. thumbnail_url: the preview photo is tracked (and purged) with its deal
--   4. toggle_vote: O(1) +1/-1 count updates instead of recounting every vote
--   5. reconcile_vote_counts(): nightly safety net for the counters
--   6. purge_old_data(): also returns thumbnail paths for Storage cleanup
--
-- Run in the Supabase SQL editor as ONE script (it is transactional: if the
-- safety check in section 4 fails, nothing is changed).
-- Apply BEFORE deploying the matching edge functions.
-- ============================================================================

-- 1. deals by poster ---------------------------------------------------------
create index if not exists deals_submitted_by_idx
  on public.deals (submitted_by_user_id, created_at desc)
  where deleted_at is null;

-- 2. search ------------------------------------------------------------------
-- get_deals ORs title / title_norm / description ILIKE '%q%'. title_norm already
-- has deals_title_trgm_idx; with these two the whole OR can use indexes.
create index if not exists deals_title_raw_trgm_idx
  on public.deals using gin (title extensions.gin_trgm_ops);
create index if not exists deals_description_trgm_idx
  on public.deals using gin (description extensions.gin_trgm_ops);

-- 3. preview photo -------------------------------------------------------------
alter table public.deals add column if not exists thumbnail_url text;

-- 4. votes ---------------------------------------------------------------------
-- Safety check: delta counting would double count if some hand-made trigger
-- on votes also maintains hot_count / cold_count. Abort if any trigger exists.
do $$
begin
  if exists (select 1 from pg_trigger where tgrelid = 'public.votes'::regclass and not tgisinternal) then
    raise exception 'votes has triggers - review them before switching toggle_vote to delta counting';
  end if;
end $$;

create or replace function public.toggle_vote(p_deal_id uuid, p_user_id uuid, p_vote_type text)
returns text
language plpgsql
security definer
set search_path = public
as $$
declare
  v_existing text;
  v_result   text;
  v_hot      integer := 0;
  v_cold     integer := 0;
begin
  if p_vote_type not in ('hot', 'cold') then
    raise exception 'invalid vote type';
  end if;

  -- Lock the deal row first: concurrent votes on the same deal queue here,
  -- so the +1/-1 updates below never race.
  perform 1 from deals where id = p_deal_id for update;

  select vote_type into v_existing from votes where deal_id = p_deal_id and user_id = p_user_id for update;

  if v_existing is null then
    insert into votes (deal_id, user_id, vote_type) values (p_deal_id, p_user_id, p_vote_type);
    v_result := p_vote_type;
    if p_vote_type = 'hot' then v_hot := 1; else v_cold := 1; end if;
  elsif v_existing = p_vote_type then
    delete from votes where deal_id = p_deal_id and user_id = p_user_id;   -- same vote again = remove
    v_result := null;
    if p_vote_type = 'hot' then v_hot := -1; else v_cold := -1; end if;
  else
    update votes set vote_type = p_vote_type, created_at = now() where deal_id = p_deal_id and user_id = p_user_id;
    v_result := p_vote_type;
    if p_vote_type = 'hot' then v_hot := 1; v_cold := -1; else v_hot := -1; v_cold := 1; end if;
  end if;

  update deals
     set hot_count  = greatest(coalesce(hot_count, 0)  + v_hot,  0),
         cold_count = greatest(coalesce(cold_count, 0) + v_cold, 0)
   where id = p_deal_id;

  return v_result;
end $$;

-- 5. nightly counter reconciliation (called by the maintenance function) -----
create or replace function public.reconcile_vote_counts()
returns integer
language sql
security definer
set search_path = public
as $$
  with actual as (
    select d.id,
           count(v.*) filter (where v.vote_type = 'hot')  as hot,
           count(v.*) filter (where v.vote_type = 'cold') as cold
      from deals d
      left join votes v on v.deal_id = d.id
     where d.is_archived = false and d.deleted_at is null
     group by d.id
  ), fixed as (
    update deals d
       set hot_count = a.hot, cold_count = a.cold
      from actual a
     where d.id = a.id
       and (coalesce(d.hot_count, 0) <> a.hot or coalesce(d.cold_count, 0) <> a.cold)
    returning 1
  )
  select count(*)::integer from fixed;
$$;

-- 6. purge: include preview photos ----------------------------------------------
create or replace function public.purge_old_data()
returns table (deals_deleted integer, reports_deleted integer, feedback_deleted integer, image_paths text[])
language plpgsql
security definer
set search_path = public
as $$
declare
  v_deals integer; v_reports integer; v_feedback integer; v_paths text[];
begin
  -- photos (full + preview) of deals that will be deleted (the Edge Function removes them)
  select coalesce(array_agg(p) filter (where p is not null), '{}') into v_paths
    from deals, unnest(array[image_url, thumbnail_url]) as p
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

-- Only the edge functions (service role) may call these.
revoke execute on function public.toggle_vote(uuid, uuid, text)  from public, anon, authenticated;
revoke execute on function public.reconcile_vote_counts()        from public, anon, authenticated;
revoke execute on function public.purge_old_data()               from public, anon, authenticated;
grant  execute on function public.toggle_vote(uuid, uuid, text)  to service_role;
grant  execute on function public.reconcile_vote_counts()        to service_role;
grant  execute on function public.purge_old_data()               to service_role;
