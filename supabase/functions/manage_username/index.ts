// ============================================================================
// manage_username
//   action "check_availability" { username }  -> { available }   (public, rate limited)
//   action "get_username"                       -> own username   (login)
//   action "register_username"  { username }  -> change own name (login)
// Usernames live on public.users (the old device-based user_identities table
// is no longer used).
// ============================================================================
import { admin, getCaller, requireUser } from "../_shared/auth.ts";
import { ApiError, clientIp, handler, ok, readJson } from "../_shared/http.ts";
import { DAY, HOUR, rateLimit } from "../_shared/ratelimit.ts";

const USERNAME_RE = /^[a-zA-Z0-9_]{3,20}$/;
const RESERVED = /^(admin|administrator|moderator|mod|support|help|egyptdealradar|dealradar|system|official|staff)$/i;

Deno.serve(handler(async (req) => {
  const body = await readJson(req);
  const action = body.action;

  if (action === "check_availability") {
    await rateLimit(`uname:check:${clientIp(req)}`, 60, HOUR, "Please slow down a little.");
    const username = String(body.username ?? "");
    if (!USERNAME_RE.test(username)) {
      throw new ApiError("VALIDATION", "Username must be 3-20 characters (letters, numbers, underscore).", { field: "username", available: false });
    }
    if (RESERVED.test(username)) return ok({ available: false });
    const { data } = await admin().from("users").select("id").ilike("username", username).maybeSingle();
    return ok({ available: !data });
  }

  if (action === "get_username") {
    const caller = await getCaller(req);
    return ok({ exists: !!caller, username: caller?.profile.username ?? null });
  }

  if (action === "register_username") {
    const caller = await requireUser(req);
    const username = String(body.username ?? "");
    if (!USERNAME_RE.test(username) || RESERVED.test(username)) {
      throw new ApiError("VALIDATION", "Username must be 3-20 characters (letters, numbers, underscore).", { field: "username" });
    }
    await rateLimit(`uname:change:${caller.profile.id}`, 3, DAY, "You can change your username up to 3 times a day.");
    const { error } = await admin().from("users").update({ username }).eq("id", caller.profile.id);
    if (error?.code === "23505") throw new ApiError("ALREADY_DONE", "This username is already taken.");
    if (error) throw error;
    return ok({ username });
  }

  throw new ApiError("VALIDATION", "Invalid action");
}));
