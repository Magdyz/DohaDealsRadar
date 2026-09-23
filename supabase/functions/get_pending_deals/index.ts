// ============================================================================
// get_pending_deals (moderator/admin)
// Deals waiting for review: new submissions ('pending') and deals auto-hidden
// after community reports ('hidden').
// ============================================================================
import { admin, requireRole } from "../_shared/auth.ts";
import { handler, ok } from "../_shared/http.ts";
import { STAFF_DEAL_COLUMNS } from "../_shared/deals.ts";

Deno.serve(handler(async (req) => {
  await requireRole(req, ["moderator", "admin"]);
  const { data, error } = await admin().from("deals")
    .select(STAFF_DEAL_COLUMNS)
    .in("status", ["pending", "hidden"])
    .is("deleted_at", null)
    .order("created_at", { ascending: true }) // oldest first = fair queue
    .limit(200);
  if (error) throw error;
  return ok({ data: data ?? [], count: data?.length ?? 0 });
}));
