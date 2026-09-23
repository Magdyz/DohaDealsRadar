-- ============================================================================
-- Orphaned deal photos - DRY RUN (read-only, deletes nothing)
-- Lists files in the `deals` bucket that no deal points to (image_url or
-- thumbnail_url) and that are older than 48 hours (so in-progress posts are
-- never included). Review the list before removing anything.
--
-- Removal must go through the Storage API (supabase.storage.from('deals')
-- .remove([...names])), NOT by deleting rows from storage.objects, which would
-- leave the files behind.
-- ============================================================================
with referenced as (
  select substring(u from '/object/public/deals/(.*)$') as name
    from public.deals d, unnest(array[d.image_url, d.thumbnail_url]) as u
   where u is not null
)
select o.name,
       o.created_at,
       (o.metadata->>'size')::bigint as bytes
  from storage.objects o
 where o.bucket_id = 'deals'
   and o.created_at < now() - interval '48 hours'
   and not exists (select 1 from referenced r where r.name = o.name)
 order by o.created_at;

-- Totals
-- with referenced as (...same as above...)
-- select count(*) as files, pg_size_pretty(sum((o.metadata->>'size')::bigint)) as size ...
