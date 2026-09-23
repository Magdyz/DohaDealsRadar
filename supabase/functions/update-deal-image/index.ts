// ============================================================================
// update-deal-image
// Swaps the preview photo for the full-quality one after posting.
// Only the deal's owner, and only with a photo from their own upload folder.
// The preview is kept as thumbnail_url (so it's tracked and purged with the
// deal instead of being left behind in Storage).
// ============================================================================
import { admin, requireUser } from "../_shared/auth.ts";
import { ApiError, handler, isUuid, ok, readJson, str } from "../_shared/http.ts";
import { assertOwnImageUrl, PUBLIC_DEAL_COLUMNS } from "../_shared/deals.ts";
import { HOUR, rateLimit } from "../_shared/ratelimit.ts";

Deno.serve(handler(async (req) => {
  const caller = await requireUser(req);
  await rateLimit(`dealimage:${caller.profile.id}`, 30, HOUR, "Please slow down a little.");
  const body = await readJson(req);
  const dealId = body.deal_id;
  const imageUrl = str(body.image_url, 500);
  if (!isUuid(dealId) || !imageUrl) throw new ApiError("VALIDATION", "Missing deal or photo.");
  assertOwnImageUrl(imageUrl, caller.authUserId);

  const { data: deal } = await admin().from("deals").select("id, submitted_by_user_id, image_url").eq("id", dealId).maybeSingle();
  if (!deal) throw new ApiError("NOT_FOUND", "Deal not found.");
  if (deal.submitted_by_user_id !== caller.profile.id) throw new ApiError("FORBIDDEN", "You can only change your own deals.");

  // Remember the preview the first time it's replaced (never overwrite an earlier one)
  const previous = deal.image_url && deal.image_url !== imageUrl ? deal.image_url : null;
  const result = await admin().from("deals").update({ image_url: imageUrl }).eq("id", dealId)
    .select(PUBLIC_DEAL_COLUMNS).single();
  if (!result.error && previous) {
    const { error: thumbError } = await admin().from("deals").update({ thumbnail_url: previous })
      .eq("id", dealId).is("thumbnail_url", null);
    // Column missing (migration not applied yet): the swap itself still succeeded
    if (thumbError) console.error("thumbnail_url not saved:", thumbError.message);
  }
  if (result.error) throw result.error;
  return ok({ data: result.data });
}));
