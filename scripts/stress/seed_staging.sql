-- ============================================================================
-- EgyptDealRadar - STAGING load-test seed data
-- ============================================================================
-- Generates, on a STAGING Supabase project only:
--   ~20,000 users   (public.users)
--   ~50,000 deals   (public.deals)   status mostly approved, some pending /
--                                    rejected / hidden / archived, spread
--                                    over the last 60 days
--   ~500,000 votes  (public.votes)   unique per (user_id, deal_id), hot/cold
--   ~5,000   reports (public.reports)
-- ...then recomputes deals.hot_count / deals.cold_count / deals.report_count
-- from the actual rows, the same way public.reconcile_vote_counts() does.
--
-- Everything is tagged so it can be found and removed later:
--   users.email  like 'seed+%@example.com'
--   deals.title  like '[SEED]%'
--
-- WHY THE GUARD BELOW: this script inserts hundreds of thousands of rows and
-- is meant to be thrown away. Running it against production would pollute
-- real data (and the cleanup section at the bottom is destructive). The
-- first statement in this file aborts unless a `public.staging_marker`
-- table exists. Create that table BY HAND, once, only on the staging
-- project, e.g. from the Supabase SQL editor:
--
--     create table public.staging_marker (
--       created_at timestamptz not null default now(),
--       note text
--     );
--     insert into public.staging_marker (note) values ('staging - safe to seed/wipe');
--
-- NEVER create public.staging_marker on the production project.
--
-- HOW TO RUN
--   psql "$STAGING_DB_URL" -v ON_ERROR_STOP=1 -f scripts/stress/seed_staging.sql
--   (or paste the whole file into the Supabase SQL editor on the staging
--   project - it stops automatically on the first error there too)
--
-- Requires supabase/migrations to already be applied (pg_trgm extension,
-- deals.title_norm/canonical_url/governorate/store columns, the partial
-- unique index on votes(user_id, deal_id), etc. - see scripts/stress/README.md
-- for the full migration order and the two gotchas in it).
--
-- Tune the row counts by editing the three `generate_series(1, N)` calls
-- below (users, deals, and the vote/report *candidate* counts - candidates
-- get deduplicated, so the final counts land a little under N).
-- Expect a few minutes end-to-end on a small staging instance.
-- ============================================================================

-- ---------------------------------------------------------------------------
-- 0. GUARD - must be the first statement in this file
-- ---------------------------------------------------------------------------
do $$
begin
  if not exists (
    select 1 from information_schema.tables
     where table_schema = 'public' and table_name = 'staging_marker'
  ) then
    raise exception
      'Refusing to run: public.staging_marker does not exist. This script is '
      'destructive/expensive and must only ever run on a STAGING project. '
      'Create the marker table by hand on staging first - see the header '
      'comment of scripts/stress/seed_staging.sql.';
  end if;
end $$;

-- Re-runnable within one session: drop any leftover helper tables from a
-- previous (partial) run before we start.
drop table if exists _seed_users;
drop table if exists _seed_deals;

-- ---------------------------------------------------------------------------
-- 1. USERS (~20,000)
-- ---------------------------------------------------------------------------
-- Bulk "population" accounts that own deals/votes/reports for load-testing
-- purposes. They have no auth_user_id (nobody can log in as them) - that's
-- fine, auth_user_id is nullable and nothing here needs a real session.
-- Real, log-in-able accounts for k6 come from scripts/stress/make_tokens.mjs
-- instead (tagged 'stress+...@example.com', a much smaller set).
create temp table _seed_users as
with rolls as (
  select
    gs as i,
    (now() - (random() * 60) * interval '1 day') as created_ts,
    random() as trust_roll,
    random() as verified_roll
  from generate_series(1, 20000) as gs
), classified as (
  select
    i, created_ts, verified_roll,
    case
      when trust_roll < 0.06 then 'trusted'
      when trust_roll < 0.35 then 'regular'
      when trust_roll < 0.97 then 'new'
      else 'flagged'
    end as trust_level
  from rolls
), ins as (
  insert into public.users (
    email, username, role, auto_approve, trust_level, strikes,
    email_verified, device_id, created_at, last_login_at, consent_at
  )
  select
    format('seed+%s@example.com', lpad(i::text, 6, '0')),
    format('SeedUser%s', lpad(i::text, 6, '0')),
    'user',
    trust_level = 'trusted',
    trust_level,
    case when trust_level = 'flagged' then 2 + floor(random() * 2)::int else 0 end,
    verified_roll < 0.95,
    format('seed-device-%s', lpad(i::text, 6, '0')),
    created_ts,
    created_ts + random() * (now() - created_ts),
    created_ts
  from classified
  returning id
)
select id, row_number() over () as rn from ins;

create unique index on _seed_users (rn);

-- ---------------------------------------------------------------------------
-- 2. DEALS (~50,000)
-- ---------------------------------------------------------------------------
-- Category / governorate: keep in sync with supabase/functions/_shared/deals.ts
-- (CATEGORIES, GOVERNORATES). Status mix: ~80% approved+live, ~10%
-- approved+archived (is_archived = true, expires_at forced into the past),
-- ~6% pending, ~3% rejected, ~1% hidden (auto-hidden-by-reports state).
-- Titles/descriptions mix English + Arabic words/phrases; images use
-- picsum.photos (public, real HTTP images) so Android scroll/image-loading
-- benchmarks have something real to fetch instead of dead URLs.
create temp table _seed_deals as
with consts as (
  select
    array['food_dining','groceries','electronics','shopping_fashion',
          'telecom','entertainment','home_services','other']::text[] as categories,
    array['cairo','giza','alexandria','qalyubia','sharqia','dakahlia','gharbia',
          'monufia','beheira','kafr_el_sheikh','damietta','port_said','ismailia',
          'suez','fayoum','beni_suef','minya','asyut','sohag','qena','luxor',
          'aswan','red_sea','new_valley','matrouh','north_sinai','south_sinai']::text[] as governorates,
    array['amazon.eg','noon.com','jumia.com.eg','btech.com','carrefouregypt.com',
          'vodafone.com.eg','orange.eg','ikea.com','hm.com','talabat.com',
          'breadfast.com']::text[] as stores,
    array['Samsung Galaxy A55 256GB','iPhone 15 Pro Case','Nike Air Max 270',
          'LG 55-inch 4K TV','KitchenAid Stand Mixer','Adidas Ultraboost Sneakers',
          'PlayStation 5 Bundle','Dell XPS 13 Laptop','Carrefour Grocery Bundle',
          'Vodafone Cash Plan','IKEA MALM Sofa','H&M Winter Jacket',
          'Breadfast Weekly Basket','Orange Fiber Internet','JBL Bluetooth Speaker',
          'Xiaomi Redmi Note 13']::text[] as products,
    array['50% Off','Buy 1 Get 1 Free','Flash Sale','Free Delivery','Weekend Offer',
          'Clearance Sale','Limited Time Deal','Student Discount','Ramadan Offer',
          'Bundle Deal','Cashback Offer','End of Season Sale',
          'Exclusive App Price']::text[] as eng_phrases,
    array['خصم كبير','عرض حصري','تخفيضات نهاية الموسم','توصيل مجاني',
          'اشتري واحد واحصل على الثاني مجانا','عرض محدود','كاش باك','خصم الطلاب',
          'عرض اليوم فقط','سعر مميز']::text[] as ar_phrases,
    array['City Center Mall','Downtown Branch','Mall of Egypt','Citystars Branch',
          'Local Souk','Food Court Branch']::text[] as venues,
    (select count(*) from _seed_users) as user_count
),
rolls as (
  select
    gs as i,
    1 + floor(random() * c.user_count)::int as urn,
    (now() - (random() * 60) * interval '1 day') as created_ts,
    random() as status_roll,
    random() < 0.85 as has_link,
    (floor(random() * 29) + 1)::int as expire_days,
    random() < 0.75 as has_price,
    round((50 + random() * 14950)::numeric, 2) as orig_price,
    0.5 + random() * 0.45 as discount_factor,
    random() < 0.20 as has_promo,
    random() < 0.30 as gov_is_nationwide,
    c.categories[1 + floor(random() * array_length(c.categories, 1))::int] as category,
    c.governorates[1 + floor(random() * array_length(c.governorates, 1))::int] as gov_named,
    c.stores[1 + floor(random() * array_length(c.stores, 1))::int] as store_host,
    c.products[1 + floor(random() * array_length(c.products, 1))::int] as product,
    c.eng_phrases[1 + floor(random() * array_length(c.eng_phrases, 1))::int] as eng_phrase,
    c.ar_phrases[1 + floor(random() * array_length(c.ar_phrases, 1))::int] as ar_phrase,
    c.venues[1 + floor(random() * array_length(c.venues, 1))::int] as venue
  from generate_series(1, 50000) as gs
  cross join consts c
),
picked as (
  select
    r.*,
    case when gov_is_nationwide then 'all_egypt' else gov_named end as governorate,
    u.id as user_id, u.username, u.device_id as user_device
  from rolls r
  join _seed_users u on u.rn = r.urn
),
finalized as (
  select
    p.*,
    case
      when status_roll < 0.80 then 'approved'   -- live
      when status_roll < 0.90 then 'approved'   -- archived subset (see make_archived)
      when status_roll < 0.96 then 'pending'
      when status_roll < 0.99 then 'rejected'
      else 'hidden'
    end as status,
    (status_roll >= 0.80 and status_roll < 0.90) as make_archived,
    format('[SEED] %s - %s %s #%s', p.product, p.eng_phrase, p.ar_phrase, p.i) as title,
    format('%s: %s. %s. Valid while stocks last / صالح حتى نفاذ الكمية.',
           p.eng_phrase, p.product, p.ar_phrase) as description
  from picked p
),
ins as (
  insert into public.deals (
    title, title_norm, description, link, canonical_url, image_url, thumbnail_url,
    location, governorate, store, category, promo_code, posted_by,
    expires_at, original_price, discounted_price, status, auto_approved, requires_review,
    hot_count, cold_count, report_count, submitted_by_user_id, submitted_by_device,
    approved_at, is_archived, created_at
  )
  select
    f.title,
    lower(regexp_replace(regexp_replace(f.title, '[^[:alnum:]\s]+', ' ', 'g'), '\s+', ' ', 'g')),
    f.description,
    case when f.has_link then 'https://www.' || f.store_host || '/deal-' || f.i else null end,
    case when f.has_link then f.store_host || '/deal-' || f.i else null end,
    format('https://picsum.photos/seed/edr%s/640/480', f.i),
    format('https://picsum.photos/seed/edr%s/320/240', f.i),
    case when not f.has_link
         then format('%s, %s', f.venue, coalesce(nullif(f.governorate, 'all_egypt'), 'Cairo'))
         else null end,
    f.governorate,
    case when f.has_link then initcap(replace(split_part(f.store_host, '.', 1), '-', ' ')) else null end,
    f.category,
    case when f.has_promo then 'SAVE' || (10 + floor(random() * 90))::int else null end,
    f.username,
    case when f.make_archived
         then least(f.created_ts + (f.expire_days || ' days')::interval, now() - interval '1 day')
         else f.created_ts + (f.expire_days || ' days')::interval
    end,
    case when f.has_price then f.orig_price else null end,
    case when f.has_price then round(f.orig_price * f.discount_factor, 2) else null end,
    f.status,
    f.status = 'approved',
    f.status in ('pending', 'hidden'),
    0, 0, 0,
    f.user_id,
    f.user_device,
    case when f.status = 'approved' then f.created_ts + (random() * 2) * interval '1 day' else null end,
    f.make_archived,
    f.created_ts
  from finalized f
  returning id, submitted_by_user_id, created_at
)
select id, row_number() over () as rn, submitted_by_user_id, created_at from ins;

create unique index on _seed_deals (rn);

-- NOTE: public.deals has AFTER INSERT / AFTER UPDATE triggers
-- (trigger_count_deal_insert, trigger_increment_approved_deals) that keep
-- users.submitted_deals_count / approved_deals_count / trust_level in sync.
-- They fire for every row above - that is intentional (it keeps the seeded
-- users' counters consistent with their seeded deals) and already accounted
-- for in the timing above. If you need to seed much more than ~50k deals
-- and want to skip that bookkeeping for speed, you can temporarily run:
--   alter table public.deals disable trigger trigger_count_deal_insert;
--   alter table public.deals disable trigger trigger_increment_approved_deals;
-- ... before the INSERT above, and re-enable them after with `enable trigger`.

-- ---------------------------------------------------------------------------
-- 3. VOTES (~500,000 candidates -> unique per user+deal after dedup)
-- ---------------------------------------------------------------------------
-- 65% hot / 35% cold. Self-votes (a user voting on their own seeded deal)
-- are skipped for realism, though the DB itself doesn't forbid them - only
-- the cast_vote Edge Function does. Uniqueness relies on the same partial
-- unique index the app uses: idx_votes_user_deal (user_id, deal_id)
-- WHERE user_id IS NOT NULL.
with n as (
  select (select count(*) from _seed_users) as un, (select count(*) from _seed_deals) as dn
),
candidates as (
  select
    1 + floor(random() * n.un)::int as urn,
    1 + floor(random() * n.dn)::int as drn,
    random() < 0.65 as is_hot
  from generate_series(1, 560000), n
),
joined as (
  select u.id as user_id, d.id as deal_id, d.created_at as deal_created_at, c.is_hot
    from candidates c
    join _seed_users u on u.rn = c.urn
    join _seed_deals d on d.rn = c.drn
   where d.submitted_by_user_id is distinct from u.id
)
insert into public.votes (deal_id, user_id, vote_type, created_at)
select deal_id, user_id, case when is_hot then 'hot' else 'cold' end,
       deal_created_at + random() * greatest(now() - deal_created_at, interval '1 hour')
  from joined
on conflict (user_id, deal_id) where user_id is not null do nothing;

-- Recompute hot_count / cold_count from the real votes (same logic as
-- public.reconcile_vote_counts(), inlined so this script is self-contained).
update public.deals d
   set hot_count  = coalesce(v.hot, 0),
       cold_count = coalesce(v.cold, 0)
  from (
    select deal_id,
           count(*) filter (where vote_type = 'hot')  as hot,
           count(*) filter (where vote_type = 'cold') as cold
      from public.votes
     group by deal_id
  ) v
 where d.id = v.deal_id;

update public.deals d
   set hot_count = 0, cold_count = 0
 where d.id in (select id from _seed_deals)
   and not exists (select 1 from public.votes v where v.deal_id = d.id)
   and (d.hot_count <> 0 or d.cold_count <> 0);

-- ---------------------------------------------------------------------------
-- 4. REPORTS (~5,000 candidates -> unique per deal+reporter after dedup)
-- ---------------------------------------------------------------------------
with n as (
  select (select count(*) from _seed_users) as un, (select count(*) from _seed_deals) as dn
),
candidates as (
  select
    1 + floor(random() * n.un)::int as urn,
    1 + floor(random() * n.dn)::int as drn,
    (array['spam', 'scam', 'expired', 'other'])[1 + floor(random() * 4)::int] as reason
  from generate_series(1, 5500), n
),
joined as (
  select u.id as reporter_user_id, d.id as deal_id, d.created_at as deal_created_at, c.reason
    from candidates c
    join _seed_users u on u.rn = c.urn
    join _seed_deals d on d.rn = c.drn
   where d.submitted_by_user_id is distinct from u.id
)
insert into public.reports (deal_id, reporter_user_id, device_id, reason, note, created_at)
select deal_id, reporter_user_id, format('user:%s', reporter_user_id), reason,
       case when reason in ('spam', 'scam')
            then 'This looks fake or misleading, please review it and take action as soon as you can, thank you.'
            else null end,
       deal_created_at + random() * greatest(now() - deal_created_at, interval '1 hour')
  from joined
on conflict (deal_id, reporter_user_id) where reporter_user_id is not null do nothing;

-- Keep deals.report_count consistent with the seeded reports (create_report
-- does this incrementally per-insert; here we just recompute it in bulk).
update public.deals d
   set report_count = r.cnt
  from (select deal_id, count(*) as cnt from public.reports group by deal_id) r
 where d.id = r.deal_id;

-- ---------------------------------------------------------------------------
-- 5. Summary + cleanup of helper tables
-- ---------------------------------------------------------------------------
select 'users' as table_name, count(*) as seeded_rows
  from public.users where email like 'seed+%@example.com'
union all
select 'deals', count(*) from public.deals where title like '[SEED]%'
union all
select 'votes', count(*) from public.votes v join public.deals d on d.id = v.deal_id where d.title like '[SEED]%'
union all
select 'reports', count(*) from public.reports r join public.deals d on d.id = r.deal_id where d.title like '[SEED]%';

drop table if exists _seed_users;
drop table if exists _seed_deals;

-- ============================================================================
-- CLEANUP (commented out on purpose)
-- Removes every row this script created. Uncomment and run on staging when
-- you're done load-testing. Order matters (children before parents).
-- ============================================================================
-- delete from public.votes   using public.deals d where votes.deal_id = d.id and d.title like '[SEED]%';
-- delete from public.reports using public.deals d where reports.deal_id = d.id and d.title like '[SEED]%';
-- -- catches any vote/report left over from a seeded user against a non-seeded deal (or vice versa)
-- delete from public.votes   using public.users u where votes.user_id = u.id and u.email like 'seed+%@example.com';
-- delete from public.reports using public.users u where reports.reporter_user_id = u.id and u.email like 'seed+%@example.com';
-- delete from public.deals where title like '[SEED]%';
-- delete from public.users where email like 'seed+%@example.com';
-- -- also see scripts/stress/make_tokens.mjs's own CLEANUP=1 mode for the
-- -- small set of real (auth + profile) accounts it creates for k6, and
-- -- delete any load-test deals k6 itself posted (title like '[LOADTEST]%').
-- delete from public.deals where title like '[LOADTEST]%';
-- select public.reconcile_vote_counts(); -- tidy up any counts touched above
