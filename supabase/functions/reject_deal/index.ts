// ============================================================================
// reject_deal (moderator/admin)
// Rejects a pending/hidden deal with a reason the poster can see. The DB
// trigger adds a strike to the poster (2 strikes remove auto-approval).
// ============================================================================
import { admin, logAction, requireRole } from "../_shared/auth.ts";
import { ApiError, handler, isUuid, ok, readJson, str } from "../_shared/http.ts";
import { STAFF_DEAL_COLUMNS } from "../_shared/deals.ts";
import { notifyDealStatus } from "../_shared/fcm.ts";

Deno.serve(handler(async (req) => {
  const caller = await requireRole(req, ["moderator", "admin"]);
  const body = await readJson(req);
  const dealId = body.deal_id;
  const reason = str(body.reason, 300) ?? "Doesn't meet our community guidelines";
  if (!isUuid(dealId)) throw new ApiError("VALIDATION", "Missing deal.");

  const { data: deal } = await admin().from("deals").select("id, status, title, submitted_by_user_id").eq("id", dealId).maybeSingle();
  if (!deal) throw new ApiError("NOT_FOUND", "Deal not found.");
  if (deal.status !== "pending" && deal.status !== "hidden") {
    throw new ApiError("VALIDATION", "Only pending or hidden deals can be rejected.");
  }

  const { data: updated, error } = await admin().from("deals")
    .update({ status: "rejected", requires_review: false, deletion_reason: reason })
    .eq("id", dealId).select(STAFF_DEAL_COLUMNS).single();
  if (error) throw error;

  await logAction("deal_rejected", caller.profile.id, { dealId, targetUserId: deal.submitted_by_user_id, reason });
  if (deal.submitted_by_user_id) await notifyDealStatus(deal.submitted_by_user_id, deal, "rejected", reason);

  return ok({ message: "Deal rejected", data: updated });
}));
