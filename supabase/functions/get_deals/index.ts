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

Deno.serve(handler(async (req) => {
  const url = new URL(req.url);
  const sortBy = ["newest", "top_week"].includes(url.searchParams.get("sort_by") ?? "") ? url.searchParams.get("sort_by")! : "hottest";
  const category = url.searchParams.get("category");
  const governorate = url.searchParams.get("governorate");
  const search = likeSafe((url.searchParams.get("q") ?? "").slice(0, 60));
  const cursor = decodeCursor(url.searchParams.get("cursor"));
  const limit = Math.max(1, Math.min(50, parseInt(url.searchParams.get("limit") ?? "20", 10) || 20));
  const page = Math.max(1, parseInt(url.searchParams.get("page") ?? "1", 10) || 1);

  const caller = await getCaller(req).catch(() => null); // feed stays public even if the token expired
  const nowIso = new Date().toISOString();

  let query = admin().from("deals")
    .select(PUBLIC_DEAL_COLUMNS, { count: cursor ? undefined : "exact" })
    .eq("status", "approved")
    .eq("is_archived", false)
    .is("deleted_at", null)
    .or(`expires_at.is.null,expires_at.gt.${q(nowIso)}`);

  if (category && category !== "all" && CATEGORIES.includes(category)) query = query.eq("category", category);
  if (governorate && GOVERNORATES.includes(governorate) && governorate !== "all_egypt") {
    query = query.or(`governorate.eq.${governorate},governorate.eq.all_egypt,governorate.is.null`);
  }
  if (search) {
    const norm = likeSafe(normalizeTitle(search));
    query = query.or(`title.ilike.${q(`%${search}%`)},title_norm.ilike.${q(`%${norm}%`)},description.ilike.${q(`%${search}%`)}`);
  }
  if (sortBy === "top_week") {
    query = query.gte("created_at", new Date(Date.now() - 7 * 86400_000).toISOString());
  }

  const byHot = sortBy !== "newest";
  if (cursor) {
    if (byHot) {
      const h = Number(cursor.h ?? 0);
      query = query.or(
        `hot_count.lt.${h},and(hot_count.eq.${h},created_at.lt.${q(cursor.c)}),and(hot_count.eq.${h},created_at.eq.${q(cursor.c)},id.lt.${q(cursor.i)})`,
      );
    } else {
      query = query.or(`created_at.lt.${q(cursor.c)},and(created_at.eq.${q(cursor.c)},id.lt.${q(cursor.i)})`);
    }
  }

  query = byHot
    ? query.order("hot_count", { ascending: false }).order("created_at", { ascending: false }).order("id", { ascending: false })
    : query.order("created_at", { ascending: false }).order("id", { ascending: false });

  const offset = cursor ? 0 : (page - 1) * limit;
  const { data, error, count } = await query.range(offset, offset + limit); // one extra row = "has more"
  if (error) throw error;

  const rows = (data ?? []) as any[];
  const hasMore = rows.length > limit;
  const deals = rows.slice(0, limit);

  if (caller && deals.length) {
    const { data: votes } = await admin().from("votes")
      .select("deal_id, vote_type")
      .eq("user_id", caller.profile.id)
      .in("deal_id", deals.map((d) => d.id));
    const byDeal = new Map((votes ?? []).map((v: any) => [v.deal_id, v.vote_type]));
    for (const d of deals) d.user_vote = byDeal.get(d.id) ?? null;
  }

  const last = deals[deals.length - 1];
  const nextCursor = hasMore && last
    ? encodeCursor(byHot ? { h: last.hot_count ?? 0, c: last.created_at, i: last.id } : { c: last.created_at, i: last.id })
    : null;

  const total = count ?? null;
  return json({
    success: true,
    data: deals,
    pagination: {
      page,
      limit,
      total: total ?? deals.length,
      totalPages: total !== null ? Math.ceil(total / limit) : null,
      hasMore,
      next_cursor: nextCursor,
    },
  }, 200, caller ? { "Cache-Control": "private, no-store" } : { "Cache-Control": "public, max-age=15" });
}));
