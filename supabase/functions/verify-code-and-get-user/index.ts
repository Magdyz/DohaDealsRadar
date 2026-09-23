// ============================================================================
// verify-code-and-get-user
// Verifies the email code and returns a real Supabase Auth session
// (access + refresh token) plus the app profile. Every other function then
// identifies the caller from that session's JWT.
//
// New accounts must send `consent: true` (PDPL: informed consent).
// ============================================================================
import { createClient } from "npm:@supabase/supabase-js@2";
import { admin } from "../_shared/auth.ts";
import { ApiError, clientIp, handler, ok, readJson, str } from "../_shared/http.ts";
import { HOUR, rateLimit } from "../_shared/ratelimit.ts";

const ADJECTIVES = [
  "Hunter", "Hero", "Scout", "Finder", "Master", "Pro", "Expert", "Ninja", "Legend", "Guru",
  "Wizard", "Champion", "Star", "King", "Queen", "Boss", "Captain", "Ace", "Elite", "Prime",
  "Cairo", "Alex", "Giza", "Nile", "Pyramid", "Pharaoh", "Zamalek", "Maadi", "Heliopolis",
  "Dokki", "Tahrir", "Sahel", "Luxor", "Aswan", "Khalili", "Yalla", "Ahwa", "Koshary",
];
const EGYPT_NUMBERS = [20, 2030, 10, 11, 12, 15, 365];

function generateUsername(): string {
  const adjective = ADJECTIVES[Math.floor(Math.random() * ADJECTIVES.length)];
  const number = Math.random() < 0.4
    ? EGYPT_NUMBERS[Math.floor(Math.random() * EGYPT_NUMBERS.length)]
    : Math.floor(Math.random() * 900) + 100;
  return `Deal${adjective}${number}`;
}

Deno.serve(handler(async (req) => {
  const body = await readJson(req);
  const email = String(body.email ?? "").trim().toLowerCase();
  const code = String(body.code ?? "").trim();
  const deviceId = str(body.device_id ?? req.headers.get("x-device-id"), 100) ?? "unknown";
  const consent = body.consent === true;

  if (!email || !/^\d{6}$/.test(code)) {
    throw new ApiError("VALIDATION", "Please enter the 6-digit code.", { field: "code" });
  }

  // Brute-force protection: 10 attempts per email per hour, 30 per IP
  await rateLimit(`verify:email:${email}`, 10, HOUR, "Too many attempts. Please request a new code later.");
  await rateLimit(`verify:ip:${clientIp(req)}`, 30, HOUR, "Too many attempts. Please try again later.");

  const anon = createClient(Deno.env.get("SUPABASE_URL")!, Deno.env.get("SUPABASE_ANON_KEY")!, {
    auth: { autoRefreshToken: false, persistSession: false },
  });
  const { data: auth, error: authError } = await anon.auth.verifyOtp({ email, token: code, type: "email" });
  if (authError || !auth?.session || !auth.user) {
    throw new ApiError("INVALID_CODE", "The code is wrong or has expired. Please try again or request a new code.");
  }

  const db = admin();
  const authUserId = auth.user.id;

  // 1. Existing profile linked to this auth user
  let { data: profile } = await db.from("users")
    .select("id, email, username, role, auto_approve, banned_at, consent_at")
    .eq("auth_user_id", authUserId).maybeSingle();

  // 2. Legacy profile (created before sessions existed) -> link it
  if (!profile) {
    const { data: legacy } = await db.from("users")
      .select("id, email, username, role, auto_approve, banned_at, consent_at")
      .ilike("email", email).maybeSingle();
    if (legacy) {
      await db.from("users").update({ auth_user_id: authUserId }).eq("id", legacy.id);
      profile = legacy;
    }
  }

  let isNew = false;
  if (!profile) {
    if (!consent) {
      throw new ApiError("VALIDATION", "Please accept the privacy policy to create an account.", { field: "consent" });
    }
    // 3. Brand new account
    for (let attempt = 0; attempt < 10 && !profile; attempt++) {
      const username = attempt < 9 ? generateUsername() : `${generateUsername()}_${Date.now() % 10000}`;
      const { data: created, error } = await db.from("users").insert({
        email,
        username,
        auth_user_id: authUserId,
        device_id: deviceId,
        email_verified: true,
        created_at: new Date().toISOString(),
        last_login_at: new Date().toISOString(),
        consent_at: new Date().toISOString(),
        total_deals_posted: 0,
        approved_deals_count: 0,
        rejected_deals_count: 0,
        trust_level: "new",
      }).select("id, email, username, role, auto_approve, banned_at, consent_at").single();
      if (created) profile = created;
      else if (error?.code !== "23505") {
        console.error("create user failed:", error?.message);
        throw new ApiError("SERVER_ERROR", "We couldn't create your account. Please try again.");
      }
    }
    if (!profile) throw new ApiError("SERVER_ERROR", "We couldn't create your account. Please try again.");
    isNew = true;
  }

  if (profile.banned_at) throw new ApiError("BANNED", "This account has been suspended.");

  await db.from("users").update({
    device_id: deviceId,
    last_login_at: new Date().toISOString(),
    ...(consent && !profile.consent_at ? { consent_at: new Date().toISOString() } : {}),
  }).eq("id", profile.id);

  const s = auth.session;
  return ok({
    message: isNew ? "Account created" : "Welcome back",
    user: {
      id: profile.id,
      email: profile.email,
      username: profile.username,
      role: profile.role,
      auto_approve: profile.auto_approve,
      is_new: isNew,
    },
    session: {
      access_token: s.access_token,
      refresh_token: s.refresh_token,
      expires_at: s.expires_at,
      expires_in: s.expires_in,
    },
  });
}));
