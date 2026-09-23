// ============================================================================
// dismiss_report (moderator/admin)
// Removes a report without action. If the deal was auto-hidden and no
// reports remain, it goes back live.
// ============================================================================
import { admin, logAction, requireRole } from "../_shared/auth.ts";
import { ApiError, handler, isUuid, ok, readJson, str } from "../_shared/http.ts";

Deno.serve(handler(async (req) => {
  const caller = await requireRole(req, ["moderator", "admin"]);
  const body = await readJson(req);
  if (!isUuid(body.report_id)) throw new ApiError("VALIDATION", "Missing report.");

  const { data: report } = await admin().from("reports").select("id, deal_id").eq("id", body.report_id).maybeSingle();
  if (!report) throw new ApiError("NOT_FOUND", "Report not found.");

  const { error } = await admin().from("reports").delete().eq("id", report.id);
  if (error) throw error;

  const { count } = await admin().from("reports").select("id", { count: "exact", head: true }).eq("deal_id", report.deal_id);
  const { data: deal } = await admin().from("deals").select("status").eq("id", report.deal_id).maybeSingle();
  await admin().from("deals").update({
    report_count: count ?? 0,
    ...(deal?.status === "hidden" && (count ?? 0) === 0 ? { status: "approved", requires_review: false } : {}),
  }).eq("id", report.deal_id);

  await logAction("report_dismissed", caller.profile.id, { dealId: report.deal_id, reason: str(body.reason, 300) });
  return ok({ message: "Report dismissed" });
}));
