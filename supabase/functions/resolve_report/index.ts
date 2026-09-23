// ============================================================================
// resolve_report (moderator/admin)
// action "delete_deal": remove the reported deal (+1 strike for the poster)
// action "warn_user":   +1 strike for the poster, deal stays
// action "ban_user":    admin only; suspends the poster's account
// ============================================================================
import { admin, logAction, requireRole } from "../_shared/auth.ts";
import { ApiError, handler, isUuid, ok, readJson, str } from "../_shared/http.ts";

Deno.serve(handler(async (req) => {
  const caller = await requireRole(req, ["moderator", "admin"]);
  const body = await readJson(req);
  const action = body.action;
  const reason = str(body.reason, 300) ?? "Reported by the community";
  if (!isUuid(body.report_id)) throw new ApiError("VALIDATION", "Missing report.");

  const { data: report } = await admin().from("reports").select("id, deal_id").eq("id", body.report_id).maybeSingle();
  if (!report) throw new ApiError("NOT_FOUND", "Report not found.");
  const { data: deal } = await admin().from("deals").select("id, submitted_by_user_id").eq("id", report.deal_id).maybeSingle();
  const posterId = deal?.submitted_by_user_id ?? null;

  const addStrike = async () => {
    if (!posterId) return;
    const { data: u } = await admin().from("users").select("strikes, role").eq("id", posterId).single();
    const strikes = (u?.strikes ?? 0) + 1;
    await admin().from("users").update({
      strikes,
      ...(strikes >= 2 && u?.role === "user" ? { auto_approve: false, trust_level: "regular" } : {}),
    }).eq("id", posterId);
  };

  let message: string;
  switch (action) {
    case "delete_deal":
      await admin().from("deals").update({
        deleted_at: new Date().toISOString(), deleted_by: caller.profile.id, deletion_reason: reason,
      }).eq("id", report.deal_id);
      await addStrike();
      message = "Deal removed";
      break;
    case "warn_user":
      await addStrike();
      message = "Poster warned";
      break;
    case "ban_user":
      if (caller.profile.role !== "admin") throw new ApiError("FORBIDDEN", "Only admins can suspend accounts.");
      if (posterId) await admin().from("users").update({ banned_at: new Date().toISOString(), auto_approve: false }).eq("id", posterId);
      await admin().from("deals").update({ deleted_at: new Date().toISOString(), deleted_by: caller.profile.id, deletion_reason: reason })
        .eq("id", report.deal_id);
      message = "Account suspended";
      break;
    default:
      throw new ApiError("VALIDATION", "Unknown action.");
  }

  await admin().from("reports").delete().eq("deal_id", report.deal_id);
  await logAction(`report_${action}`, caller.profile.id, { dealId: report.deal_id, targetUserId: posterId, reason });
  return ok({ message: `Report resolved: ${message}` });
}));
