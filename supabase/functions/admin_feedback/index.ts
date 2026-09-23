// ============================================================================
// admin_feedback (admin only - feedback may contain the sender's email)
//   { action: "list", status?: pending|reviewed|resolved|archived, page? }
//   { action: "update", feedback_id, status, notes? }
// ============================================================================
import { admin, requireRole } from "../_shared/auth.ts";
import { ApiError, handler, isUuid, ok, readJson, str } from "../_shared/http.ts";

const STATUSES = ["pending", "reviewed", "resolved", "archived"];
const COLUMNS = "id, user_id, feedback_text, email, status, created_at, reviewed_at, reviewed_by, notes";

Deno.serve(handler(async (req) => {
  const caller = await requireRole(req, ["admin"]);
  const body = await readJson(req);

  if (body.action === "update") {
    if (!isUuid(body.feedback_id)) throw new ApiError("VALIDATION", "Missing feedback.");
    if (!STATUSES.includes(body.status)) throw new ApiError("VALIDATION", "Invalid status.");
    const { data, error } = await admin().from("feedback").update({
      status: body.status,
      notes: str(body.notes, 1000),
      reviewed_at: new Date().toISOString(),
      reviewed_by: caller.profile.id,
    }).eq("id", body.feedback_id).select(COLUMNS).maybeSingle();
    if (error) throw error;
    if (!data) throw new ApiError("NOT_FOUND", "Feedback not found.");
    return ok({ data });
  }

  // list
  const page = Math.max(1, Number(body.page) || 1);
  const limit = 20;
  let query = admin().from("feedback").select(COLUMNS, { count: "exact" })
    .order("created_at", { ascending: false })
    .range((page - 1) * limit, page * limit - 1);
  if (STATUSES.includes(body.status)) query = query.eq("status", body.status);
  const { data, error, count } = await query;
  if (error) throw error;

  // Attach the sender's username (feedback.user_id is the profile id when logged in)
  const ids = [...new Set((data ?? []).map((f: any) => f.user_id).filter(Boolean))];
  const names = new Map<string, string>();
  if (ids.length) {
    const { data: users } = await admin().from("users").select("id, username").in("id", ids);
    for (const u of users ?? []) names.set((u as any).id, (u as any).username);
  }
  const rows = (data ?? []).map((f: any) => ({ ...f, username: f.user_id ? names.get(f.user_id) ?? null : null }));

  const total = count ?? 0;
  return ok({ data: rows, pagination: { page, limit, total, totalPages: Math.ceil(total / limit), hasMore: page * limit < total } });
}));
