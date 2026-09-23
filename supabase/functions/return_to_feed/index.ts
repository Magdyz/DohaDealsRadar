// ============================================================================
// return_to_feed (admin)
// Un-archives a deal and extends its expiry (default 10 days).
// ============================================================================
import { admin, logAction, requireRole } from "../_shared/auth.ts";
import { ApiError, handler, isUuid, ok, readJson } from "../_shared/http.ts";
import { STAFF_DEAL_COLUMNS } from "../_shared/deals.ts";

Deno.serve(handler(async (req) => {
  const caller = await requireRole(req, ["admin"]);
  const body = await readJson(req);
  const dealId = body.deal_id;
  if (!isUuid(dealId)) throw new ApiError("VALIDATION", "Missing deal.");
  const days = Math.max(1, Math.min(30, Number(body.expires_in_days) || 10));

  const { data, error } = await admin().from("deals").update({
    is_archived: false,
    expired_votes: 0,
    expires_at: new Date(Date.now() + days * 86400_000).toISOString(),
  }).eq("id", dealId).select(STAFF_DEAL_COLUMNS).maybeSingle();
  if (error) throw error;
  if (!data) throw new ApiError("NOT_FOUND", "Deal not found.");

  await admin().from("deal_expiry_votes").delete().eq("deal_id", dealId);
  await logAction("deal_returned_to_feed", caller.profile.id, { dealId });
  return ok({ message: "Deal returned to feed", data });
}));
