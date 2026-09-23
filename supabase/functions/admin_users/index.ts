// ============================================================================
// admin_users (admin only) - every change is written to the audit log.
//   { action: "list", q?, filter?: all|moderators|admins|banned|trusted, page? }
//   { action: "ban"|"unban", target_user_id, reason? }
//   { action: "set_auto_approve", target_user_id, value: boolean }
//   { action: "reset_strikes", target_user_id }
// Role changes stay in update_user_role.
// Guards: admins can't ban themselves or another admin (demote first).
// ============================================================================
import { admin, logAction, requireRole } from "../_shared/auth.ts";
import { ApiError, handler, isUuid, ok, readJson, str } from "../_shared/http.ts";

const COLUMNS =
  "id, username, email, role, trust_level, auto_approve, strikes, approved_deals_count, rejected_deals_count, banned_at, created_at, last_login_at";
const likeSafe = (s: string) => s.replace(/[%_\\,()"]/g, " ").trim();

Deno.serve(handler(async (req) => {
  const caller = await requireRole(req, ["admin"]);
  const body = await readJson(req);
  const action = body.action ?? "list";

  if (action === "list") {
    const page = Math.max(1, Number(body.page) || 1);
    const limit = 30;
    let query = admin().from("users").select(COLUMNS, { count: "exact" })
      .order("created_at", { ascending: false })
      .range((page - 1) * limit, page * limit - 1);
    switch (body.filter) {
      case "moderators": query = query.eq("role", "moderator"); break;
      case "admins": query = query.eq("role", "admin"); break;
      case "banned": query = query.not("banned_at", "is", null); break;
      case "trusted": query = query.eq("auto_approve", true); break;
    }
    const q = likeSafe(str(body.q, 60) ?? "");
    if (q) query = query.or(`username.ilike.%${q}%,email.ilike.%${q}%`);
    const { data, error, count } = await query;
    if (error) throw error;
    const total = count ?? 0;
    return ok({ data: data ?? [], pagination: { page, limit, total, totalPages: Math.ceil(total / limit), hasMore: page * limit < total } });
  }

  const target = body.target_user_id;
  if (!isUuid(target)) throw new ApiError("VALIDATION", "Missing user.");
  const { data: user } = await admin().from("users").select(COLUMNS).eq("id", target).maybeSingle();
  if (!user) throw new ApiError("NOT_FOUND", "User not found.");
  const reason = str(body.reason, 300);

  let patch: Record<string, unknown>;
  let audit: () => Promise<void>;
  switch (action) {
    case "ban":
      if (target === caller.profile.id) throw new ApiError("FORBIDDEN", "You can't ban yourself.");
      if (user.role === "admin") throw new ApiError("FORBIDDEN", "Remove the admin role before banning this account.");
      patch = { banned_at: new Date().toISOString(), auto_approve: false };
      audit = () => logAction("user_banned", caller.profile.id, { targetUserId: target, reason });
      break;
    case "unban":
      patch = { banned_at: null };
      audit = () => logAction("user_unbanned", caller.profile.id, { targetUserId: target, reason });
      break;
    case "set_auto_approve": {
      if (typeof body.value !== "boolean") throw new ApiError("VALIDATION", "Missing value.");
      patch = { auto_approve: body.value };
      audit = () => logAction("auto_approve_changed", caller.profile.id, {
        targetUserId: target, oldValue: String(!!user.auto_approve), newValue: String(body.value),
      });
      break;
    }
    case "reset_strikes":
      patch = { strikes: 0 };
      audit = () => logAction("strikes_reset", caller.profile.id, { targetUserId: target, oldValue: String(user.strikes ?? 0), newValue: "0" });
      break;
    default:
      throw new ApiError("VALIDATION", "Unknown action.");
  }

  const { data, error } = await admin().from("users").update(patch).eq("id", target).select(COLUMNS).single();
  if (error) throw error;
  await audit(); // only after the change succeeded
  return ok({ data });
}));
