// ============================================================================
// create_report
// Logged-in users only; one report per account per deal; 5 per day.
// Auto-hide: when 3 different users report the same live deal it is hidden
// (status 'hidden') until a moderator reviews it.
// ============================================================================
import { admin, requireUser } from "../_shared/auth.ts";
import { ApiError, handler, isUuid, ok, readJson, str } from "../_shared/http.ts";
import { DAY, rateLimit } from "../_shared/ratelimit.ts";
import { checkIntegrity } from "../_shared/integrity.ts";

const REASONS = ["spam", "scam", "expired", "other"];
const AUTO_HIDE_AT = 3;

Deno.serve(handler(async (req) => {
  const caller = await requireUser(req);
  const body = await readJson(req);
  const dealId = body.deal_id;
  const reason = String(body.reason ?? "").toLowerCase();
  const note = str(body.note, 1000);

  if (!isUuid(dealId)) throw new ApiError("VALIDATION", "Missing deal.");
  if (!REASONS.includes(reason)) throw new ApiError("VALIDATION", "Please choose a reason.", { field: "reason" });
  if ((reason === "scam" || reason === "spam") && (!note || note.length < 30)) {
    throw new ApiError("VALIDATION", "Please add at least 30 characters explaining the problem.", { field: "note" });
  }

  await rateLimit(`report:${caller.profile.id}`, 5, DAY, "You've reached today's report limit. Please try again tomorrow.");
  await checkIntegrity(req, "create_report");

  const { data: deal } = await admin().from("deals").select("id, status, submitted_by_user_id").eq("id", dealId).maybeSingle();
  if (!deal) throw new ApiError("NOT_FOUND", "Deal not found.");
  if (deal.submitted_by_user_id === caller.profile.id) throw new ApiError("OWN_DEAL", "You can't report your own deal.");

  const { data, error } = await admin().from("reports").insert({
    deal_id: dealId,
    reporter_user_id: caller.profile.id,
    device_id: `user:${caller.profile.id}`,
    reason,
    note,
    created_at: new Date().toISOString(),
  }).select("id, deal_id, reason, note, created_at");

  if (error) {
    if (error.code === "23505") throw new ApiError("ALREADY_DONE", "You've already reported this deal. Thank you!");
    throw error;
  }

  const { count } = await admin().from("reports").select("id", { count: "exact", head: true }).eq("deal_id", dealId);
  const updates: Record<string, unknown> = { report_count: count ?? 0 };
  if ((count ?? 0) >= AUTO_HIDE_AT && deal.status === "approved") {
    updates.status = "hidden";
    updates.requires_review = true;
  }
  await admin().from("deals").update(updates).eq("id", dealId);

  return ok({ message: "Report submitted", data });
}));
