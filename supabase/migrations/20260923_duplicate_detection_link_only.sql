-- Duplicate detection: drop image-hash matching, block only on identical link.
-- Photo hashing threw false positives on plain white-background product shots
-- (different sellers, same stock photo), so it no longer blocks a post.
-- Only an exact match on canonical_url is treated as a hard duplicate now.
-- A similar title is still surfaced (via the title_norm trigram index) but is
-- a soft, overridable warning, not a block.
-- Locked down to service_role since it's only ever called from edge functions.

create or replace function public.find_similar_deals(
  p_canonical_url text,
  p_title_norm    text,
  p_image_hash    bigint,          -- accepted for compatibility, ignored
  p_exclude_id    uuid default null
)
returns table (id uuid, title text, image_url text, match_type text, score real)
language sql
stable
security definer
set search_path = public, extensions
as $$
  select m.id, m.title, m.image_url, m.match_type, m.score
    from (
      select distinct on (x.id) x.id, x.title, x.image_url, x.match_type, x.score
        from (
          -- same product link (only when the link has a real path, not just a domain)
          select d.id, d.title, d.image_url, 'url'::text as match_type, 1.0::real as score
            from deals d
           where p_canonical_url is not null
             and position('/' in p_canonical_url) > 0
             and d.canonical_url = p_canonical_url
             and d.status in ('approved', 'pending')
             and d.is_archived = false
             and d.deleted_at is null
             and d.created_at > now() - interval '30 days'
             and (p_exclude_id is null or d.id <> p_exclude_id)
          union all
          -- very similar title (% uses the trigram index, then a stricter cut-off)
          select d.id, d.title, d.image_url, 'title'::text, similarity(d.title_norm, p_title_norm)
            from deals d
           where p_title_norm is not null
             and d.title_norm % p_title_norm
             and similarity(d.title_norm, p_title_norm) >= 0.6
             and d.status in ('approved', 'pending')
             and d.is_archived = false
             and d.deleted_at is null
             and d.created_at > now() - interval '30 days'
             and (p_exclude_id is null or d.id <> p_exclude_id)
        ) x
       order by x.id, x.score desc
    ) m
   order by m.score desc
   limit 5;
$$;

-- Only the edge functions (service role) may call it.
revoke execute on function public.find_similar_deals(text, text, bigint, uuid) from public, anon, authenticated;
grant  execute on function public.find_similar_deals(text, text, bigint, uuid) to service_role;
