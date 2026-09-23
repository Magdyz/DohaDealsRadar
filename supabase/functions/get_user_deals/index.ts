// ============================================================================
// get_user_deals
// Your own deals (any status, with rejection reasons), or - for staff - any
// user's deals via target_user_id.
// ============================================================================
import { admin, isStaff, requireUser } from "../_shared/auth.ts";
import { ApiError, handler, isUuid, ok, readJson } from "../_shared/http.ts";
import { STAFF_DEAL_COLUMNS } from "../_shared/deals.ts";

Deno.serve(handler(async (req) => {
  const caller = await requireUser(req);
  const body = await readJson(req);
  const target = isUuid(body.target_user_id) ? body.target_user_id : caller.profile.id;
  if (target !== caller.profile.id && !isStaff(caller)) {
    throw new ApiError("FORBIDDEN", "You can only view your own deals.");
  }
  const page = Math.max(1, Number(body.page) || 1);
  const limit = Math.max(1, Math.min(50, Number(body.limit) || 20));
  const offset = (page - 1) * limit;

  const { data, error, count } = await admin().from("deals")
    .select(STAFF_DEAL_COLUMNS, { count: "exact" })
    .eq("submitted_by_user_id", target)
    .is("deleted_at", null)
    .order("created_at", { ascending: false })
    .range(offset, offset + limit - 1);
  if (error) throw error;

  const totalPages = Math.ceil((count ?? 0) / limit);

  // Status counts for the account header (page 1 only; same filter as the list)
  let stats: Record<string, number> | undefined;
  if (page === 1) {
    const countStatus = async (status: string) => {
      const { count: c, error: e } = await admin().from("deals")
        .select("id", { count: "exact", head: true })
        .eq("submitted_by_user_id", target)
        .is("deleted_at", null)
        .eq("status", status);
      if (e) throw e;
      return c ?? 0;
    };
    const [approved, pending, rejected] = await Promise.all(["approved", "pending", "rejected"].map(countStatus));
    stats = { total: count ?? 0, approved, pending, rejected };
  }

  return ok({ data: data ?? [], pagination: { page, limit, total: count ?? 0, totalPages, hasMore: page < totalPages }, stats });
}));
