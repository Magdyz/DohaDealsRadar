// ============================================================================
// get_archived_deals (moderator/admin)
// ============================================================================
import { admin, requireRole } from "../_shared/auth.ts";
import { handler, json } from "../_shared/http.ts";
import { STAFF_DEAL_COLUMNS } from "../_shared/deals.ts";

Deno.serve(handler(async (req) => {
  await requireRole(req, ["moderator", "admin"]);
  const url = new URL(req.url);
  const page = Math.max(1, parseInt(url.searchParams.get("page") ?? "1", 10) || 1);
  const limit = Math.max(1, Math.min(50, parseInt(url.searchParams.get("limit") ?? "20", 10) || 20));
  const offset = (page - 1) * limit;

  const { data, error, count } = await admin().from("deals")
    .select(STAFF_DEAL_COLUMNS, { count: "exact" })
    .eq("is_archived", true)
    .is("deleted_at", null)
    .order("created_at", { ascending: false })
    .range(offset, offset + limit - 1);
  if (error) throw error;

  const totalPages = Math.ceil((count ?? 0) / limit);
  return json({
    success: true,
    data: data ?? [],
    pagination: { page, limit, total: count ?? 0, total_pages: totalPages, has_next: page < totalPages, has_previous: page > 1 },
  });
}));
