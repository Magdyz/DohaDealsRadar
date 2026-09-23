// ============================================================================
// get_user_profile
//   own profile      -> full (incl. email, trust progress)
//   staff viewing    -> public fields + masked email
//   everyone else    -> public fields only (username, role, counts)
// Never returns device ids.
// ============================================================================
import { admin, getCaller, isStaff } from "../_shared/auth.ts";
import { ApiError, handler, isUuid, ok, readJson } from "../_shared/http.ts";

Deno.serve(handler(async (req) => {
  const caller = await getCaller(req);
  const body = await readJson(req);
  const id = isUuid(body.user_id) ? body.user_id : caller?.profile.id;
  if (!id) throw new ApiError("UNAUTHORIZED", "Please log in to continue.");

  const { data: u, error } = await admin().from("users")
    .select("id, email, username, email_verified, role, auto_approve, trust_level, approved_deals_count, rejected_deals_count, strikes, created_at, last_login_at, banned_at")
    .eq("id", id).maybeSingle();
  if (error) throw error;
  if (!u) throw new ApiError("NOT_FOUND", "User not found.");

  const self = caller?.profile.id === u.id;
  const staff = isStaff(caller);
  const [name, domain] = (u.email ?? "").split("@");

  return ok({
    message: "User profile",
    data: {
      id: u.id,
      username: u.username,
      role: u.role,
      auto_approve: u.auto_approve,
      trust_level: u.trust_level,
      approved_deals_count: u.approved_deals_count ?? 0,
      created_at: u.created_at,
      email: self ? u.email : staff ? `${name.slice(0, 1)}***@${domain ?? ""}` : null,
      email_verified: self ? u.email_verified : undefined,
      last_login_at: self || staff ? u.last_login_at : null,
      rejected_deals_count: self || staff ? u.rejected_deals_count : undefined,
      strikes: self || staff ? u.strikes : undefined,
      banned: staff ? !!u.banned_at : undefined,
    },
  });
}));
