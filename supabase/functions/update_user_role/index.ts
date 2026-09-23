// ============================================================================
// update_user_role (admin)
// { target_user_id, new_role: user|moderator|admin } - audited.
// Admins can't demote themselves (prevents locking the project out).
// ============================================================================
import { admin, logAction, requireRole } from "../_shared/auth.ts";
import { ApiError, handler, isUuid, ok, readJson } from "../_shared/http.ts";

Deno.serve(handler(async (req) => {
  const caller = await requireRole(req, ["admin"]);
  const body = await readJson(req);
  const target = body.target_user_id;
  const newRole = body.new_role;
  if (!isUuid(target)) throw new ApiError("VALIDATION", "Missing user.");
  if (!["user", "moderator", "admin"].includes(newRole)) throw new ApiError("VALIDATION", "Invalid role.");
  if (target === caller.profile.id && newRole !== "admin") throw new ApiError("FORBIDDEN", "You can't remove your own admin role.");

  const { data: old } = await admin().from("users").select("role").eq("id", target).maybeSingle();
  if (!old) throw new ApiError("NOT_FOUND", "User not found.");

  const { error } = await admin().from("users").update({
    role: newRole,
    auto_approve: newRole !== "user" ? true : undefined,
  }).eq("id", target);
  if (error) throw error;

  await logAction("user_role_changed", caller.profile.id, { targetUserId: target, oldValue: old.role, newValue: newRole });
  return ok({ message: `Role updated to ${newRole}`, old_role: old.role, new_role: newRole });
}));
