// ============================================================================
// delete_deal
// Soft delete: the poster can remove their own deal; moderators/admins any.
// ============================================================================
import { admin, isStaff, logAction, requireUser } from "../_shared/auth.ts";
import { ApiError, handler, isUuid, ok, readJson, str } from "../_shared/http.ts";
import { STAFF_DEAL_COLUMNS } from "../_shared/deals.ts";
import { HOUR, rateLimit } from "../_shared/ratelimit.ts";

Deno.serve(handler(async (req) => {
  const caller = await requireUser(req);
  const body = await readJson(req);
  const dealId = body.deal_id;
  if (!isUuid(dealId)) throw new ApiError("VALIDATION", "Missing deal.");

  const { data: deal } = await admin().from("deals").select("id, submitted_by_user_id, deleted_at").eq("id", dealId).maybeSingle();
  if (!deal) throw new ApiError("NOT_FOUND", "Deal not found.");
  const isOwner = deal.submitted_by_user_id === caller.profile.id;
  if (!isOwner && !isStaff(caller)) throw new ApiError("FORBIDDEN", "You can only delete your own deals.");
  if (!isStaff(caller)) await rateLimit(`delete:${caller.profile.id}`, 30, HOUR, "Please slow down a little.");

  const reason = str(body.reason, 300) ?? (isOwner ? "Deleted by owner" : "Removed by moderator");
  const { data: updated, error } = await admin().from("deals").update({
    deleted_at: new Date().toISOString(),
    deleted_by: caller.profile.id,
    deletion_reason: reason,
  }).eq("id", dealId).select(STAFF_DEAL_COLUMNS).single();
  if (error) throw error;

  await logAction("deal_deleted", caller.profile.id, { dealId, targetUserId: deal.submitted_by_user_id, reason });
  return ok({ message: "Deal deleted", data: updated });
}));
