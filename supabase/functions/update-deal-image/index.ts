// ============================================================================
// update-deal-image
// Swaps the preview photo for the full-quality one after posting.
// Only the deal's owner, and only with a photo from their own upload folder.
// ============================================================================
import { admin, requireUser } from "../_shared/auth.ts";
import { ApiError, handler, isUuid, ok, readJson, str } from "../_shared/http.ts";
import { assertOwnImageUrl, PUBLIC_DEAL_COLUMNS } from "../_shared/deals.ts";

Deno.serve(handler(async (req) => {
  const caller = await requireUser(req);
  const body = await readJson(req);
  const dealId = body.deal_id;
  const imageUrl = str(body.image_url, 500);
  if (!isUuid(dealId) || !imageUrl) throw new ApiError("VALIDATION", "Missing deal or photo.");
  assertOwnImageUrl(imageUrl, caller.authUserId);

  const { data: deal } = await admin().from("deals").select("id, submitted_by_user_id").eq("id", dealId).maybeSingle();
  if (!deal) throw new ApiError("NOT_FOUND", "Deal not found.");
  if (deal.submitted_by_user_id !== caller.profile.id) throw new ApiError("FORBIDDEN", "You can only change your own deals.");

  const { data, error } = await admin().from("deals").update({ image_url: imageUrl }).eq("id", dealId)
    .select(PUBLIC_DEAL_COLUMNS).single();
  if (error) throw error;
  return ok({ data });
}));
