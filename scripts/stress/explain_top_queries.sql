-- ============================================================================
-- EgyptDealRadar - scripts/stress/explain_top_queries.sql
--
-- EXPLAIN (ANALYZE, BUFFERS) for the SQL shapes behind the app's hottest
-- Edge Function paths, so you can see index usage / buffer hits before and
-- after a k6 run. Run this AFTER seed_staging.sql (empty tables give
-- meaningless plans) and ideally once before the k6 run and once during/
-- right after it, so you can compare.
--
-- Mostly read-only. The one exception is clearly marked (section 6,
-- "toggle_vote-like update") - it is wrapped in BEGIN/ROLLBACK so it never
-- actually changes seeded data.
--
-- Run with: psql "$STAGING_DB_URL" -f scripts/stress/explain_top_queries.sql
-- or paste sections one at a time into the Supabase SQL editor (EXPLAIN
-- ANALYZE output is easiest to read one query at a time there).
-- ============================================================================

-- ---------------------------------------------------------------------------
-- 1. Feed: hottest page (get_deals, sort_by=hottest, no filters, page 1)
-- Mirrors supabase/functions/get_deals/index.ts:
--   .eq('status','approved').eq('is_archived',false).is('deleted_at',null)
--   .or('expires_at.is.null,expires_at.gt.<now>')
--   .order('hot_count',desc).order('created_at',desc).order('id',desc)
--   .range(0, limit)              -- limit+1 rows fetched to detect hasMore
-- Expected plan: Index Scan on deals_feed_hot_idx.
-- ---------------------------------------------------------------------------
explain (analyze, buffers)
select id, title, description, image_url, link, posted_by, created_at, expires_at,
       status, hot_count, cold_count, location, category, promo_code, is_archived,
       original_price, discounted_price, governorate, store, report_count, expired_votes,
       auto_approved, deleted_at, deletion_reason
  from public.deals
 where status = 'approved'
   and is_archived = false
   and deleted_at is null
   and (expires_at is null or expires_at > now())
 order by hot_count desc, created_at desc, id desc
 limit 21;

-- ---------------------------------------------------------------------------
-- 2. Feed: by category (get_deals?category=electronics&sort_by=hottest)
-- Expected plan: still deals_feed_hot_idx with category as a Filter, or
-- idx_deals_status_category / idx_deals_category if the planner prefers it -
-- worth checking which one it actually picks under real data volumes.
-- ---------------------------------------------------------------------------
explain (analyze, buffers)
select id, title, description, image_url, link, posted_by, created_at, expires_at,
       status, hot_count, cold_count, location, category, promo_code, is_archived,
       original_price, discounted_price, governorate, store, report_count, expired_votes,
       auto_approved, deleted_at, deletion_reason
  from public.deals
 where status = 'approved'
   and is_archived = false
   and deleted_at is null
   and (expires_at is null or expires_at > now())
   and category = 'electronics'
 order by hot_count desc, created_at desc, id desc
 limit 21;

-- ---------------------------------------------------------------------------
-- 3. Feed: search "phone" across title / title_norm / description
-- Mirrors get_deals' `.or('title.ilike...,title_norm.ilike...,description.ilike...')`
-- Expected plan: Bitmap Or across deals_title_raw_trgm_idx /
-- deals_title_trgm_idx / deals_description_trgm_idx (all added in
-- 20260924_scale_tier1.sql / 20260922_security_hardening.sql) instead of a
-- sequential scan.
-- ---------------------------------------------------------------------------
explain (analyze, buffers)
select id, title, description, image_url, link, posted_by, created_at, expires_at,
       status, hot_count, cold_count, location, category, promo_code, is_archived,
       original_price, discounted_price, governorate, store, report_count, expired_votes,
       auto_approved, deleted_at, deletion_reason
  from public.deals
 where status = 'approved'
   and is_archived = false
   and deleted_at is null
   and (expires_at is null or expires_at > now())
   and (title ilike '%phone%' or title_norm ilike '%phone%' or description ilike '%phone%')
 order by hot_count desc, created_at desc, id desc
 limit 21;

-- ---------------------------------------------------------------------------
-- 4. User deals: get_user_deals (submitted_by_user_id, page 1)
-- Mirrors: .eq('submitted_by_user_id', target).is('deleted_at', null)
--          .order('created_at', desc).range(0, limit-1)
-- Picks a real, currently-existing user via a subquery so this runs without
-- edits - swap in a literal uuid once you know one for a cleaner plan.
-- Expected plan: Index Scan on deals_submitted_by_idx.
-- ---------------------------------------------------------------------------
explain (analyze, buffers)
select id, title, description, image_url, link, posted_by, created_at, expires_at,
       status, hot_count, cold_count, location, category, promo_code, is_archived,
       original_price, discounted_price, governorate, store, report_count, expired_votes,
       auto_approved, deleted_at, deletion_reason,
       submitted_by_user_id, approved_by, approved_at, requires_review
  from public.deals
 where submitted_by_user_id = (
         select submitted_by_user_id from public.deals
          where submitted_by_user_id is not null
          order by created_at desc limit 1
       )
   and deleted_at is null
 order by created_at desc
 limit 20 offset 0;

-- get_user_deals also runs 3 head-count queries (approved/pending/rejected)
-- on page 1 for the account-header stats. Representative shape:
explain (analyze, buffers)
select count(*)
  from public.deals
 where submitted_by_user_id = (
         select submitted_by_user_id from public.deals
          where submitted_by_user_id is not null
          order by created_at desc limit 1
       )
   and deleted_at is null
   and status = 'approved';

-- ---------------------------------------------------------------------------
-- 5. find_similar_deals() - called by both submit_deal and check_duplicate
-- EXPLAIN on a `select * from find_similar_deals(...)` only shows a single
-- "Function Scan" node (Postgres doesn't expand into a SQL-language
-- function's body unless it gets inlined), so first the plain call for a
-- realistic end-to-end time, then the function's body expanded inline
-- (matching supabase/migrations/20260923_duplicate_detection_link_only.sql)
-- for the real breakdown.
-- ---------------------------------------------------------------------------
explain (analyze, buffers)
select * from public.find_similar_deals(
  'amazon.eg/dp/EXAMPLE123',       -- p_canonical_url
  'samsung galaxy a55 256gb',      -- p_title_norm
  null,                             -- p_image_hash (accepted, ignored)
  null                              -- p_exclude_id
);

-- expanded (same query the function above actually runs):
explain (analyze, buffers)
select m.id, m.title, m.image_url, m.match_type, m.score
  from (
    select distinct on (x.id) x.id, x.title, x.image_url, x.match_type, x.score
      from (
        select d.id, d.title, d.image_url, 'url'::text as match_type, 1.0::real as score
          from public.deals d
         where position('/' in 'amazon.eg/dp/EXAMPLE123') > 0
           and d.canonical_url = 'amazon.eg/dp/EXAMPLE123'
           and d.status in ('approved', 'pending')
           and d.is_archived = false
           and d.deleted_at is null
           and d.created_at > now() - interval '30 days'
        union all
        select d.id, d.title, d.image_url, 'title'::text, similarity(d.title_norm, 'samsung galaxy a55 256gb')
          from public.deals d
         where d.title_norm % 'samsung galaxy a55 256gb'
           and similarity(d.title_norm, 'samsung galaxy a55 256gb') >= 0.6
           and d.status in ('approved', 'pending')
           and d.is_archived = false
           and d.deleted_at is null
           and d.created_at > now() - interval '30 days'
      ) x
     order by x.id, x.score desc
  ) m
 order by m.score desc
 limit 5;

-- ---------------------------------------------------------------------------
-- 6. toggle_vote()-like update (cast_vote's RPC) - WRITES, rolled back
-- Mirrors the statements inside public.toggle_vote() (supabase/migrations/
-- 20260924_scale_tier1.sql): lock the caller's existing vote row, lock the
-- deal row, then apply the +1/-1 delta.
-- ---------------------------------------------------------------------------
begin;

-- 6a. lock + read the caller's existing vote for this deal (if any)
explain (analyze, buffers)
with sample as (select deal_id, user_id from public.votes order by random() limit 1)
select v.vote_type
  from public.votes v
  join sample s on v.deal_id = s.deal_id and v.user_id = s.user_id
 for update;

-- 6b. lock the deal row (toggle_vote locks the deal first, so concurrent
-- votes on the same hot deal serialize here - this is the contention point
-- the hot_deal k6 scenario is meant to stress)
explain (analyze, buffers)
select 1 from public.deals
 where id = (select deal_id from public.votes order by random() limit 1)
 for update;

-- 6c. the +1/-1 delta write
explain (analyze, buffers)
update public.deals
   set hot_count = greatest(coalesce(hot_count, 0) + 1, 0)
 where id = (select deal_id from public.votes order by random() limit 1)
returning id, hot_count, cold_count;

rollback; -- undo 6c - this file must not change seeded data

-- ---------------------------------------------------------------------------
-- 7. Index usage: which indexes are actually being hit
-- Run this before AND after a k6 run and diff idx_scan to see which
-- deals/votes/reports/users indexes the load actually exercised, and which
-- ones sat unused (candidates for dropping in a real capacity review).
-- ---------------------------------------------------------------------------
select schemaname, relname as table_name, indexrelname as index_name,
       idx_scan, idx_tup_read, idx_tup_fetch,
       pg_size_pretty(pg_relation_size(indexrelid)) as index_size
  from pg_stat_user_indexes
 where schemaname = 'public'
 order by idx_scan asc, pg_relation_size(indexrelid) desc;

-- ---------------------------------------------------------------------------
-- 8. Table sizes (total, table-only, and dead-tuple bloat)
-- Watch n_dead_tup after a 500k-row vote-writing k6 run: cast_vote's
-- UPDATE deals SET hot_count/cold_count on every vote makes `deals` the
-- most likely table to bloat between autovacuum runs.
-- ---------------------------------------------------------------------------
select relname as table_name,
       pg_size_pretty(pg_total_relation_size(relid)) as total_size,
       pg_size_pretty(pg_relation_size(relid)) as table_size,
       pg_size_pretty(pg_total_relation_size(relid) - pg_relation_size(relid)) as indexes_toast_size,
       n_live_tup, n_dead_tup,
       last_autovacuum, last_autoanalyze
  from pg_stat_user_tables
 where schemaname = 'public'
 order by pg_total_relation_size(relid) desc;

-- ---------------------------------------------------------------------------
-- 9. OPTIONAL: slowest statements overall, if pg_stat_statements is enabled
-- (Supabase dashboard: Database > Extensions > pg_stat_statements). Not
-- enabled by default, so this is commented out.
-- ---------------------------------------------------------------------------
-- select query, calls, round(total_exec_time::numeric, 1) as total_ms,
--        round(mean_exec_time::numeric, 2) as mean_ms,
--        round(max_exec_time::numeric, 2) as max_ms, rows
--   from pg_stat_statements
--  where query ilike '%deals%' or query ilike '%votes%' or query ilike '%reports%'
--  order by total_exec_time desc
--  limit 20;
