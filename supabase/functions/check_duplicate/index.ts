// ============================================================================
// check_duplicate
// Called while posting (before submit) to warn early:
//   { link?, title?, image_hash? }  ->  { duplicates: [...], blocking: bool }
// `blocking` = an active deal with the same link already exists.
// ============================================================================
import { admin, requireUser } from "../_shared/auth.ts";
import { handler, ok, readJson, str } from "../_shared/http.ts";
import { canonicalizeUrl, normalizeTitle } from "../_shared/deals.ts";
import { HOUR, rateLimit } from "../_shared/ratelimit.ts";

Deno.serve(handler(async (req) => {
  const caller = await requireUser(req);
  await rateLimit(`dupcheck:${caller.profile.id}`, 120, HOUR, "Please slow down a little.");
  const body = await readJson(req);

  let canonical: string | null = null;
  const link = str(body.link, 2000);
  if (link) {
    try { canonical = canonicalizeUrl(new URL(link)); } catch { /* ignore bad links here */ }
  }
  const title = str(body.title, 200);
  const hash = typeof body.image_hash === "string" && /^-?\d{1,20}$/.test(body.image_hash) ? body.image_hash : null;

  const { data, error } = await admin().rpc("find_similar_deals", {
    p_canonical_url: canonical,
    p_title_norm: title ? normalizeTitle(title) : null,
    p_image_hash: hash,
    p_exclude_id: null,
  });
  if (error) throw error;

  const duplicates = (data ?? []) as Array<{ id: string; title: string; image_url: string; match_type: string; score: number }>;
  return ok({
    duplicates,
    blocking: duplicates.some((d) => d.match_type === "url"),
  });
}));
