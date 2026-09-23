// ============================================================================
// permanent_delete_deal (admin)
// Deletes the deal row (votes/reports cascade) and its photo from Storage.
// ============================================================================
import { admin, logAction, requireRole } from "../_shared/auth.ts";
import { ApiError, handler, isUuid, ok, readJson } from "../_shared/http.ts";
import { storagePathFromPublicUrl } from "../_shared/storage.ts";

Deno.serve(handler(async (req) => {
  const caller = await requireRole(req, ["admin"]);
  const { deal_id: dealId } = await readJson(req);
  if (!isUuid(dealId)) throw new ApiError("VALIDATION", "Missing deal.");

  const { data: deal } = await admin().from("deals").select("id, title, image_url, submitted_by_user_id").eq("id", dealId).maybeSingle();
  if (!deal) throw new ApiError("NOT_FOUND", "Deal not found.");

  const path = storagePathFromPublicUrl(deal.image_url);
  if (path) {
    const { error } = await admin().storage.from("deals").remove([path]);
    if (error) console.error("image delete failed:", error.message);
  }

  const { error } = await admin().from("deals").delete().eq("id", dealId);
  if (error) throw error;

  await logAction("deal_permanently_deleted", caller.profile.id, { targetUserId: deal.submitted_by_user_id, reason: deal.title });
  return ok({ data: null, error: null });
}));
