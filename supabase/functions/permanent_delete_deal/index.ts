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

  // "*" works both before and after the thumbnail_url column exists
  const { data: deal } = await admin().from("deals").select("*").eq("id", dealId).maybeSingle();
  if (!deal) throw new ApiError("NOT_FOUND", "Deal not found.");

  // Full photo + preview (thumbnail_url)
  const paths = [deal.image_url, deal.thumbnail_url].map(storagePathFromPublicUrl).filter(Boolean) as string[];
  if (paths.length) {
    const { error } = await admin().storage.from("deals").remove(paths);
    if (error) console.error("image delete failed:", error.message);
  }

  const { error } = await admin().from("deals").delete().eq("id", dealId);
  if (error) throw error;

  await logAction("deal_permanently_deleted", caller.profile.id, { targetUserId: deal.submitted_by_user_id, reason: deal.title });
  return ok({ data: null, error: null });
}));
