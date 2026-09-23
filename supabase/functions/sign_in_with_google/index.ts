// ============================================================================
// sign_in_with_google
// Exchanges a Google ID token (from Android Credential Manager) for a real
// Supabase Auth session, and makes sure the caller has an app profile.
//
// Privacy: the Google account's email is used only to find an existing
// account. New profiles are created WITHOUT an email — the identity lives in
// Supabase Auth, and our own table only ever holds a random username.
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

const PROFILE_COLUMNS = "id, username, role, auto_approve, banned_at, consent_at";

Deno.serve(handler(async (req) => {
  const body = await readJson(req);
  const idToken = str(body.id_token, 4000);
  const nonce = str(body.nonce, 200); // raw nonce; Google carries its SHA-256 in the token
  const deviceId = str(body.device_id ?? req.headers.get("x-device-id"), 100) ?? "unknown";
  const consent = body.consent === true;

  if (!idToken) {
    throw new ApiError("VALIDATION", "Google sign-in didn't complete. Please try again.");
  }

  // Google itself is the brake on abuse here; this only stops token replay floods.
  await rateLimit(`google:ip:${clientIp(req)}`, 30, HOUR, "Too many sign-in attempts. Please try again later.");

  const anon = createClient(Deno.env.get("SUPABASE_URL")!, Deno.env.get("SUPABASE_ANON_KEY")!, {
    auth: { autoRefreshToken: false, persistSession: false },
  });
  const { data: auth, error: authError } = await anon.auth.signInWithIdToken({
    provider: "google",
    token: idToken,
    ...(nonce ? { nonce } : {}),
  });

  if (authError || !auth?.session || !auth.user) {
    console.error("google signInWithIdToken failed:", authError?.status, authError?.message);
    throw new ApiError("UNAUTHORIZED", "We couldn't verify your Google account. Please try again.");
  }

  const db = admin();
  const authUserId = auth.user.id;
  const googleEmail = (auth.user.email ?? "").trim().toLowerCase();

  // 1. Profile already linked to this auth user
  let { data: profile } = await db.from("users")
    .select(PROFILE_COLUMNS).eq("auth_user_id", authUserId).maybeSingle();

  // 2. Account created back when sign-in was by email code -> adopt it, then
  //    forget the email: from here on the auth user id is the only link.
  if (!profile && googleEmail) {
    const { data: legacy } = await db.from("users")
      .select(PROFILE_COLUMNS).ilike("email", googleEmail).maybeSingle();
    if (legacy) {
      await db.from("users")
        .update({ auth_user_id: authUserId, email: null, email_verified: true })
        .eq("id", legacy.id);
      profile = legacy;
    }
  }

  let isNew = false;
  if (!profile) {
    if (!consent) {
      throw new ApiError("VALIDATION", "Please accept the privacy policy to create an account.", { field: "consent" });
    }
    for (let attempt = 0; attempt < 10 && !profile; attempt++) {
      const username = attempt < 9 ? generateUsername() : `${generateUsername()}_${Date.now() % 10000}`;
      const { data: created, error } = await db.from("users").insert({
        email: null, // never stored: Supabase Auth already holds the identity
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
      }).select(PROFILE_COLUMNS).single();
      if (created) profile = created;
      else if (error?.code !== "23505") { // username collision -> try another
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
