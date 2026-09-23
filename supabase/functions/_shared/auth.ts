// ============================================================================
// Identity & authorization for Edge Functions
//
// The caller's identity comes ONLY from the Supabase Auth JWT in the
// Authorization header (verified with auth.getUser). Request bodies are never
// trusted for identity (no user_id / admin_user_id / user_email parameters).
// ============================================================================
import { createClient, SupabaseClient } from "npm:@supabase/supabase-js@2";
import { ApiError } from "./http.ts";

export type Role = "user" | "moderator" | "admin";

export interface Profile {
  id: string;
  auth_user_id: string | null;
  email: string;
  username: string;
  role: Role;
  auto_approve: boolean;
  trust_level: string | null;
  approved_deals_count: number | null;
  strikes: number;
  banned_at: string | null;
  created_at: string;
}

export interface Caller {
  authUserId: string;
  email: string;
  profile: Profile;
}

let _admin: SupabaseClient | null = null;

/** Service-role client (bypasses RLS) - only used server-side. */
export function admin(): SupabaseClient {
  if (!_admin) {
    _admin = createClient(Deno.env.get("SUPABASE_URL")!, Deno.env.get("SUPABASE_SERVICE_ROLE_KEY")!, {
      auth: { autoRefreshToken: false, persistSession: false },
    });
  }
  return _admin;
}

const PROFILE_COLUMNS =
  "id, auth_user_id, email, username, role, auto_approve, trust_level, approved_deals_count, strikes, banned_at, created_at";

function bearer(req: Request): string | null {
  const h = req.headers.get("Authorization") ?? "";
  const m = h.match(/^Bearer\s+(.+)$/i);
  return m ? m[1].trim() : null;
}

/**
 * Returns the logged-in caller, or null for anonymous requests (anon key or
 * no header). Throws UNAUTHORIZED for an invalid/expired user token so the
 * app can refresh or ask the user to log in again.
 */
export async function getCaller(req: Request): Promise<Caller | null> {
  const token = bearer(req);
  if (!token) return null;
  if (token === Deno.env.get("SUPABASE_ANON_KEY")) return null;

  // Quick check: anon/service JWTs have no "sub"; user JWTs do.
  let payload: Record<string, unknown> = {};
  try {
    payload = JSON.parse(atob(token.split(".")[1].replace(/-/g, "+").replace(/_/g, "/")));
  } catch {
    throw new ApiError("UNAUTHORIZED", "Invalid session");
  }
  if (payload.role === "anon" || !payload.sub) return null;

  const { data, error } = await admin().auth.getUser(token);
  if (error || !data?.user) throw new ApiError("UNAUTHORIZED", "Session expired. Please log in again.");

  const authUser = data.user;
  const { data: profile } = await admin()
    .from("users")
    .select(PROFILE_COLUMNS)
    .eq("auth_user_id", authUser.id)
    .maybeSingle();

  if (!profile) throw new ApiError("UNAUTHORIZED", "Account not found. Please log in again.");
  if (profile.banned_at) throw new ApiError("BANNED", "This account has been suspended.");

  return { authUserId: authUser.id, email: authUser.email ?? profile.email, profile: profile as Profile };
}

export async function requireUser(req: Request): Promise<Caller> {
  const caller = await getCaller(req);
  if (!caller) throw new ApiError("UNAUTHORIZED", "Please log in to continue.");
  return caller;
}

export async function requireRole(req: Request, roles: Role[]): Promise<Caller> {
  const caller = await requireUser(req);
  if (!roles.includes(caller.profile.role)) throw new ApiError("FORBIDDEN", "You don't have permission to do this.");
  return caller;
}

export const isStaff = (c: Caller | null) => !!c && (c.profile.role === "admin" || c.profile.role === "moderator");

/** Internal calls (cron, function-to-function) authenticate with the service key. */
export function requireServiceCall(req: Request) {
  const token = bearer(req);
  if (!token || token !== Deno.env.get("SUPABASE_SERVICE_ROLE_KEY")) {
    throw new ApiError("FORBIDDEN", "Internal endpoint");
  }
}

export async function logAction(
  action: string,
  actorProfileId: string | null,
  opts: { dealId?: string | null; targetUserId?: string | null; oldValue?: string | null; newValue?: string | null; reason?: string | null } = {},
) {
  const { error } = await admin().from("audit_log").insert({
    action_type: action,
    user_id: actorProfileId,
    deal_id: opts.dealId ?? null,
    target_user_id: opts.targetUserId ?? null,
    old_value: opts.oldValue ?? null,
    new_value: opts.newValue ?? null,
    reason: opts.reason ?? null,
  });
  if (error) console.error("audit_log insert failed:", error.message);
}
