// ============================================================================
// cast_vote
// Logged-in users only; one vote per account per deal.
// Toggle semantics: sending the same vote again removes it; the opposite vote
// switches it. Response includes `user_vote` (the caller's resulting vote).
// Blocks voting on your own deal and on deals that aren't live.
// ============================================================================
import { admin, requireUser } from "../_shared/auth.ts";
import { ApiError, handler, isUuid, ok, readJson } from "../_shared/http.ts";
import { PUBLIC_DEAL_COLUMNS } from "../_shared/deals.ts";
import { HOUR, rateLimit } from "../_shared/ratelimit.ts";
import { checkIntegrity } from "../_shared/integrity.ts";

Deno.serve(handler(async (req) => {
  const caller = await requireUser(req);
  const body = await readJson(req);
  const dealId = body.deal_id;
  const voteType = body.vote_type;
  if (!isUuid(dealId)) throw new ApiError("VALIDATION", "Missing deal.");
  if (voteType !== "hot" && voteType !== "cold") throw new ApiError("VALIDATION", "Invalid vote.");

  await rateLimit(`vote:${caller.profile.id}`, 120, HOUR, "You're voting very fast. Please wait a moment.");
  await checkIntegrity(req, "cast_vote");

  const { data: deal } = await admin().from("deals")
    .select("id, status, is_archived, deleted_at, expires_at, submitted_by_user_id")
    .eq("id", dealId).maybeSingle();
  if (!deal) throw new ApiError("NOT_FOUND", "Deal not found.");
  if (deal.status !== "approved" || deal.is_archived || deal.deleted_at) {
    throw new ApiError("DEAL_UNAVAILABLE", "This deal is no longer available.");
  }
  if (deal.submitted_by_user_id === caller.profile.id) {
    throw new ApiError("OWN_DEAL", "You can't vote on your own deal.");
  }

  const { data: userVote, error } = await admin().rpc("toggle_vote", {
    p_deal_id: dealId,
    p_user_id: caller.profile.id,
    p_vote_type: voteType,
  });
  if (error) throw error;

  const { data: updated, error: fetchError } = await admin().from("deals").select(PUBLIC_DEAL_COLUMNS).eq("id", dealId).single();
  if (fetchError) throw fetchError;
  (updated as any).user_vote = userVote ?? null;

  return ok({ message: userVote ? `Vote recorded: ${userVote}` : "Vote removed", user_vote: userVote ?? null, data: updated });
}));
