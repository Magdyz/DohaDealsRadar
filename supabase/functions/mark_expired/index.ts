// ============================================================================
// mark_expired
// "Is this deal expired?" - logged-in users confirm a deal has ended.
// After 3 different users confirm, the deal is archived automatically.
// ============================================================================
import { admin, requireUser } from "../_shared/auth.ts";
import { ApiError, handler, isUuid, ok, readJson } from "../_shared/http.ts";
import { DAY, rateLimit } from "../_shared/ratelimit.ts";

const ARCHIVE_AT = 3;

Deno.serve(handler(async (req) => {
  const caller = await requireUser(req);
  const { deal_id: dealId } = await readJson(req);
  if (!isUuid(dealId)) throw new ApiError("VALIDATION", "Missing deal.");
  await rateLimit(`expired:${caller.profile.id}`, 30, DAY, "Please try again later.");

  const { data: deal } = await admin().from("deals").select("id, status, is_archived, submitted_by_user_id").eq("id", dealId).maybeSingle();
  if (!deal) throw new ApiError("NOT_FOUND", "Deal not found.");
  if (deal.is_archived) return ok({ archived: true, expired_votes: ARCHIVE_AT });

  const { error } = await admin().from("deal_expiry_votes").insert({ deal_id: dealId, user_id: caller.profile.id });
  if (error && error.code !== "23505") throw error;
  if (error?.code === "23505") throw new ApiError("ALREADY_DONE", "Thanks, you already told us this deal ended.");

  const { count } = await admin().from("deal_expiry_votes").select("deal_id", { count: "exact", head: true }).eq("deal_id", dealId);
  // The poster (or staff) confirming counts as final
  const final = (count ?? 0) >= ARCHIVE_AT || deal.submitted_by_user_id === caller.profile.id ||
    caller.profile.role === "admin" || caller.profile.role === "moderator";
  await admin().from("deals").update({ expired_votes: count ?? 0, ...(final ? { is_archived: true } : {}) }).eq("id", dealId);

  return ok({ archived: final, expired_votes: count ?? 0 });
}));
