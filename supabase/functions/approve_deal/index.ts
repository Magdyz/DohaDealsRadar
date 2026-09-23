// ============================================================================
// approve_deal (moderator/admin)
// Publishes a pending deal (or restores a report-hidden one). Counters and
// trust levels are updated by the DB trigger only (no double counting).
// Broadcasts new deals; tells the poster their deal is live.
// ============================================================================
import { admin, logAction, requireRole } from "../_shared/auth.ts";
import { ApiError, handler, isUuid, ok, readJson } from "../_shared/http.ts";
import { STAFF_DEAL_COLUMNS } from "../_shared/deals.ts";
import { notifyDealStatus, notifyNewDeal } from "../_shared/fcm.ts";

Deno.serve(handler(async (req) => {
  const caller = await requireRole(req, ["moderator", "admin"]);
  const { deal_id: dealId } = await readJson(req);
  if (!isUuid(dealId)) throw new ApiError("VALIDATION", "Missing deal.");

  const { data: deal } = await admin().from("deals")
    .select("id, status, title, category, image_url, submitted_by_user_id, approved_at")
    .eq("id", dealId).maybeSingle();
  if (!deal) throw new ApiError("NOT_FOUND", "Deal not found.");

  if (deal.status === "approved") {
    const { data } = await admin().from("deals").select(STAFF_DEAL_COLUMNS).eq("id", dealId).single();
    return ok({ message: "Deal already approved", data });
  }
  if (deal.status !== "pending" && deal.status !== "hidden") {
    throw new ApiError("VALIDATION", "Only pending or hidden deals can be approved.");
  }

  const wasHidden = deal.status === "hidden";
  const { data: updated, error } = await admin().from("deals").update({
    status: "approved",
    requires_review: false,
    approved_by: caller.profile.id,
    approved_at: deal.approved_at ?? new Date().toISOString(),
    ...(wasHidden ? { report_count: 0 } : {}),
  }).eq("id", dealId).select(STAFF_DEAL_COLUMNS).single();
  if (error) throw error;

  if (wasHidden) await admin().from("reports").delete().eq("deal_id", dealId); // reviewed: reports cleared

  await logAction(wasHidden ? "deal_restored" : "deal_approved", caller.profile.id, {
    dealId, targetUserId: deal.submitted_by_user_id,
  });

  if (!wasHidden) {
    await notifyNewDeal({ id: deal.id, title: deal.title, category: deal.category, image_url: deal.image_url });
    if (deal.submitted_by_user_id) await notifyDealStatus(deal.submitted_by_user_id, deal, "approved");
  }

  return ok({ message: wasHidden ? "Deal restored" : "Deal approved", data: updated });
}));
