// ============================================================================
// get_deals - public feed
//
// Query params:
//   sort_by     hottest (default) | newest | top_week
//   category    one of CATEGORIES (omit / "all" = everything)
//   governorate one of GOVERNORATES (omit = everywhere; "all_egypt" deals are
//               always included because they apply nationwide/online)
//   q           search text (English/Arabic, title)
//   cursor      opaque cursor from the previous page (keyset pagination)
//   page        legacy offset pagination (used only when no cursor is given)
//   limit       1..50 (default 20)
//   bundle      "categories": first page of every category in one call
//               -> { bundle: [{ category, data, pagination }] } (instant tabs)
//
// Only approved, non-archived, non-deleted, non-expired deals. Only public
// columns. If the caller is logged in, each deal carries `user_vote`.
// ============================================================================
import { admin, getCaller } from "../_shared/auth.ts";
import { handler, json } from "../_shared/http.ts";
import { CATEGORIES, GOVERNORATES, normalizeTitle, PUBLIC_DEAL_COLUMNS } from "../_shared/deals.ts";

type Cursor = { h?: number; c: string; i: string };

function encodeCursor(c: Cursor) {
  return btoa(JSON.stringify(c)).replace(/=+$/, "");
}
function decodeCursor(s: string | null): Cursor | null {
  if (!s) return null;
  try {
    const c = JSON.parse(atob(s));
    return typeof c?.c === "string" && typeof c?.i === "string" ? c : null;
  } catch {
    return null;
  }
}
/** Escape a value for a PostgREST filter inside or(...) */
const q = (v: string | number) => `"${String(v).replace(/"/g, '\\"')}"`;
const likeSafe = (s: string) => s.replace(/[%_\\,()"]/g, " ").trim();

type PageParams = {
  sortBy: string;
  category: string | null;
  governorate: string | null;
  search: string;
  cursor: Cursor | null;
  limit: number;
  page: number;
};

/** One feed page (same filters and order for the normal feed and the category bundle). */
async function feedPage(p: PageParams, nowIso: string) {
  let query = admin().from("deals")
    .select(PUBLIC_DEAL_COLUMNS) // no exact count: "has more" comes from one extra row
    .eq("status", "approved")
    .eq("is_archived", false)
    .is("deleted_at", null)
    .or(`expires_at.is.null,expires_at.gt.${q(nowIso)}`);

  if (p.category && p.category !== "all" && CATEGORIES.includes(p.category)) query = query.eq("category", p.category);
  if (p.governorate && GOVERNORATES.includes(p.governorate) && p.governorate !== "all_egypt") {
    query = query.or(`governorate.eq.${p.governorate},governorate.eq.all_egypt,governorate.is.null`);
  }
  if (p.search) {
    const norm = likeSafe(normalizeTitle(p.search));
    query = query.or(`title.ilike.${q(`%${p.search}%`)},title_norm.ilike.${q(`%${norm}%`)},description.ilike.${q(`%${p.search}%`)}`);
  }
  if (p.sortBy === "top_week") {
    query = query.gte("created_at", new Date(Date.now() - 7 * 86400_000).toISOString());
  }

  const byHot = p.sortBy !== "newest";
  if (p.cursor) {
    if (byHot) {
      const h = Number(p.cursor.h ?? 0);
      query = query.or(
        `hot_count.lt.${h},and(hot_count.eq.${h},created_at.lt.${q(p.cursor.c)}),and(hot_count.eq.${h},created_at.eq.${q(p.cursor.c)},id.lt.${q(p.cursor.i)})`,
      );
    } else {
      query = query.or(`created_at.lt.${q(p.cursor.c)},and(created_at.eq.${q(p.cursor.c)},id.lt.${q(p.cursor.i)})`);
    }
  }

  query = byHot
    ? query.order("hot_count", { ascending: false }).order("created_at", { ascending: false }).order("id", { ascending: false })
    : query.order("created_at", { ascending: false }).order("id", { ascending: false });

  const offset = p.cursor ? 0 : (p.page - 1) * p.limit;
  const { data, error } = await query.range(offset, offset + p.limit); // one extra row = "has more"
  if (error) throw error;

  const rows = (data ?? []) as any[];
  const hasMore = rows.length > p.limit;
  const deals = rows.slice(0, p.limit);
  const last = deals[deals.length - 1];
  const nextCursor = hasMore && last
    ? encodeCursor(byHot ? { h: last.hot_count ?? 0, c: last.created_at, i: last.id } : { c: last.created_at, i: last.id })
    : null;

  return {
    deals,
    pagination: { page: p.page, limit: p.limit, total: deals.length, totalPages: null, hasMore, next_cursor: nextCursor },
  };
}

/** Adds the caller's own vote to each deal (one query for any number of deals). */
async function attachVotes(callerId: string, deals: any[]) {
  if (!deals.length) return;
  const { data: votes } = await admin().from("votes")
    .select("deal_id, vote_type")
    .eq("user_id", callerId)
    .in("deal_id", deals.map((d) => d.id));
  const byDeal = new Map((votes ?? []).map((v: any) => [v.deal_id, v.vote_type]));
  for (const d of deals) d.user_vote = byDeal.get(d.id) ?? null;
}

Deno.serve(handler(async (req) => {
  const url = new URL(req.url);
  const sortBy = ["newest", "top_week"].includes(url.searchParams.get("sort_by") ?? "") ? url.searchParams.get("sort_by")! : "hottest";
  const governorate = url.searchParams.get("governorate");
  const limit = Math.max(1, Math.min(50, parseInt(url.searchParams.get("limit") ?? "20", 10) || 20));

  const caller = await getCaller(req).catch(() => null); // feed stays public even if the token expired
  const nowIso = new Date().toISOString();
  const cacheHeader: Record<string, string> = caller
    ? { "Cache-Control": "private, no-store" }
    : { "Cache-Control": "public, max-age=15" };

  // First page of every category in one request (the app prefetches it once so
  // switching category tabs is instant). limit is per category, max 20.
  if (url.searchParams.get("bundle") === "categories") {
    const perCategory = Math.min(limit, 20);
    const pages = await Promise.all(CATEGORIES.map((category) =>
      feedPage({ sortBy, category, governorate, search: "", cursor: null, limit: perCategory, page: 1 }, nowIso)
    ));
    if (caller) await attachVotes(caller.profile.id, pages.flatMap((p) => p.deals));
    return json({
      success: true,
      bundle: CATEGORIES.map((category, i) => ({ category, data: pages[i].deals, pagination: pages[i].pagination })),
    }, 200, cacheHeader);
  }

  const { deals, pagination } = await feedPage({
    sortBy,
    category: url.searchParams.get("category"),
    governorate,
    search: likeSafe((url.searchParams.get("q") ?? "").slice(0, 60)),
    cursor: decodeCursor(url.searchParams.get("cursor")),
    limit,
    page: Math.max(1, parseInt(url.searchParams.get("page") ?? "1", 10) || 1),
  }, nowIso);
  if (caller) await attachVotes(caller.profile.id, deals);

  return json({ success: true, data: deals, pagination }, 200, cacheHeader);
}));
