// ============================================================================
// send_notification - INTERNAL ONLY (service-role key required)
// Kept for manual/admin broadcasts from the backend; deal notifications are
// sent directly via _shared/fcm.ts. Clients can no longer call this.
// Body: { dealId, title, category, imageUrl? }
// ============================================================================
import { requireServiceCall } from "../_shared/auth.ts";
import { ApiError, handler, ok, readJson, str } from "../_shared/http.ts";
import { notifyNewDeal } from "../_shared/fcm.ts";

Deno.serve(handler(async (req) => {
  requireServiceCall(req);
  const body = await readJson(req);
  const dealId = str(body.dealId, 64);
  const title = str(body.title, 200);
  if (!dealId || !title) throw new ApiError("VALIDATION", "Missing dealId or title");
  await notifyNewDeal({ id: dealId, title, category: str(body.category, 40) ?? "other", image_url: str(body.imageUrl, 500) });
  return ok({ message: "Notifications sent" });
}));
