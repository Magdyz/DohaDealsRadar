// ============================================================================
// admin_audit_log (admin only)
//   { category?: all|security|deals|reports, page? }
// Security = role changes, bans, auto-approve / strikes, account deletions.
// Rows carry the actor's and target's usernames and the deal title.
// ============================================================================
import { admin, requireRole } from "../_shared/auth.ts";
import { handler, ok, readJson } from "../_shared/http.ts";

const CATEGORIES: Record<string, string[]> = {
  security: ["user_role_changed", "user_banned", "user_unbanned", "auto_approve_changed", "strikes_reset", "account_deleted", "report_ban_user"],
  deals: ["deal_approved", "deal_restored", "deal_rejected", "deal_deleted", "deal_permanently_deleted", "deal_returned_to_feed", "deal_marked_ended"],
  reports: ["report_dismissed", "report_delete_deal", "report_warn_user", "report_ban_user"],
};

Deno.serve(handler(async (req) => {
  await requireRole(req, ["admin"]);
  const body = await readJson(req);
  const page = Math.max(1, Number(body.page) || 1);
  const limit = 30;

  let query = admin().from("audit_log")
    .select("*", { count: "exact" })   // "*": audit_log predates the tracked migrations
    .order("created_at", { ascending: false })
    .range((page - 1) * limit, page * limit - 1);
  const types = CATEGORIES[body.category];
  if (types) query = query.in("action_type", types);
  const { data, error, count } = await query;
  if (error) throw error;

  const rows = (data ?? []) as any[];
  const userIds = [...new Set(rows.flatMap((r) => [r.user_id, r.target_user_id]).filter(Boolean))];
  const dealIds = [...new Set(rows.map((r) => r.deal_id).filter(Boolean))];
  const [usersRes, dealsRes] = await Promise.all([
    userIds.length ? admin().from("users").select("id, username").in("id", userIds) : Promise.resolve({ data: [] }),
    dealIds.length ? admin().from("deals").select("id, title").in("id", dealIds) : Promise.resolve({ data: [] }),
  ]);
  const names = new Map(((usersRes as any).data ?? []).map((u: any) => [u.id, u.username]));
  const titles = new Map(((dealsRes as any).data ?? []).map((d: any) => [d.id, d.title]));

  const out = rows.map((r) => ({
    ...r,
    actor_username: r.user_id ? names.get(r.user_id) ?? null : null,
    target_username: r.target_user_id ? names.get(r.target_user_id) ?? null : null,
    deal_title: r.deal_id ? titles.get(r.deal_id) ?? null : null,
  }));
  const total = count ?? 0;
  return ok({ data: out, pagination: { page, limit, total, totalPages: Math.ceil(total / limit), hasMore: page * limit < total } });
}));
