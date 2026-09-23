// ============================================================================
// EgyptDealRadar - scripts/stress/make_tokens.mjs
//
// Creates N real Supabase Auth + app-profile accounts on a STAGING project,
// using the same email-OTP flow as scripts/backend_e2e.mjs (admin
// generate_link -> verify-code-and-get-user), and writes their access
// tokens to scripts/stress/tokens.json for k6_feed_vote_post.js.
//
// New accounts are capped at 3 submit_deal calls/day (see submit_deal's
// `dailyLimit`, supabase/functions/submit_deal/index.ts). To make the k6
// "post" scenario meaningful without spending its whole budget on
// RATE_LIMITED responses, each account is promoted straight after creation
// to trust_level='trusted' + auto_approve=true (dailyLimit -> 20/day) via a
// direct PATCH to public.users with the service-role key - the same trick
// scripts/backend_e2e.mjs uses to make its "Carol" account an admin.
//
// Usage (PowerShell):
//   $env:SUPABASE_URL         = "https://<staging-ref>.functions.supabase.co"
//   $env:SUPABASE_PROJECT_URL = "https://<staging-ref>.supabase.co"
//   $env:ANON_KEY             = "<staging anon key>"
//   $env:SERVICE_KEY          = "<staging service_role key>"
//   $env:N                    = "800"
//   node scripts/stress/make_tokens.mjs
//
// Usage (bash):
//   SUPABASE_URL=https://<staging-ref>.functions.supabase.co \
//   SUPABASE_PROJECT_URL=https://<staging-ref>.supabase.co \
//   ANON_KEY=... SERVICE_KEY=... N=800 \
//   node scripts/stress/make_tokens.mjs
//
// Output:
//   scripts/stress/tokens.json      - JSON array of access tokens (one per
//                                      VU), consumed directly by k6 via
//                                      TOKENS_FILE.
//   scripts/stress/tokens_meta.json - {email, profile_id, auth_user_id,
//                                      token}[] - kept so this script can
//                                      clean the accounts up again.
//
// Cleanup (deletes only the accounts this script created):
//   CLEANUP=1 SUPABASE_PROJECT_URL=... SERVICE_KEY=... node scripts/stress/make_tokens.mjs
//
// Refuses to run (create OR cleanup) against the production project ref
// (nzchbnshkrkdqpcawohu), whether it shows up in SUPABASE_URL or
// SUPABASE_PROJECT_URL.
// ============================================================================
import fs from "node:fs";
import path from "node:path";
import { fileURLToPath } from "node:url";

const PROD_REF = "nzchbnshkrkdqpcawohu";

const FN = (process.env.SUPABASE_URL ?? "").replace(/\/$/, "");
const BASE = (process.env.SUPABASE_PROJECT_URL ?? "").replace(/\/$/, "");
const ANON = process.env.ANON_KEY;
const SERVICE = process.env.SERVICE_KEY;
const N = Math.max(1, parseInt(process.env.N ?? "800", 10));
const CONCURRENCY = Math.max(1, parseInt(process.env.CONCURRENCY ?? "8", 10));
const CLEANUP = process.env.CLEANUP === "1";

const OUT_DIR = fileURLToPath(new URL(".", import.meta.url));
const TOKENS_FILE = path.join(OUT_DIR, "tokens.json");
const META_FILE = path.join(OUT_DIR, "tokens_meta.json");

function refuseIfProd(...urls) {
  for (const u of urls) {
    if (u && u.includes(PROD_REF)) {
      console.error(
        `Refusing to run: "${u}" looks like the PRODUCTION project (${PROD_REF}). ` +
        `This script creates and deletes accounts - point it at a STAGING project only.`,
      );
      process.exit(1);
    }
  }
}
refuseIfProd(FN, BASE);

if (!BASE || !SERVICE) {
  console.error("Missing env. Required at minimum: SUPABASE_PROJECT_URL, SERVICE_KEY");
  process.exit(1);
}
if (!CLEANUP && (!FN || !ANON)) {
  console.error("Missing env. Required to create tokens: SUPABASE_URL (functions base), ANON_KEY");
  process.exit(1);
}

async function call(fn, body = {}, token = ANON) {
  const res = await fetch(`${FN}/${fn}`, {
    method: "POST",
    headers: { apikey: ANON, Authorization: `Bearer ${token}`, "Content-Type": "application/json", "x-device-id": "make-tokens" },
    body: JSON.stringify(body),
  });
  let json = null;
  try { json = await res.json(); } catch { /* empty body */ }
  return { status: res.status, body: json };
}

async function adminApi(pathname, init = {}) {
  const res = await fetch(`${BASE}${pathname}`, {
    ...init,
    headers: { apikey: SERVICE, Authorization: `Bearer ${SERVICE}`, "Content-Type": "application/json", ...(init.headers ?? {}) },
  });
  const text = await res.text();
  let body = null;
  try { body = text ? JSON.parse(text) : null; } catch { body = text; }
  return { status: res.status, body };
}

/** Pulls the `sub` claim (the Supabase Auth user id) out of an access token. */
function authUserIdFromToken(token) {
  try {
    const payload = JSON.parse(Buffer.from(token.split(".")[1].replace(/-/g, "+").replace(/_/g, "/"), "base64").toString("utf8"));
    return payload.sub ?? null;
  } catch {
    return null;
  }
}

const RUN = Date.now().toString(36);

async function makeOne(i) {
  const email = `stress+${RUN}-${String(i).padStart(5, "0")}@example.com`;

  let gen = await adminApi("/auth/v1/admin/generate_link", {
    method: "POST",
    body: JSON.stringify({ type: "magiclink", email }),
  });
  if (!gen.body?.email_otp) {
    // brand new email: create the auth user first, then generate its code
    await adminApi("/auth/v1/admin/users", { method: "POST", body: JSON.stringify({ email, email_confirm: true }) });
    gen = await adminApi("/auth/v1/admin/generate_link", { method: "POST", body: JSON.stringify({ type: "magiclink", email }) });
  }
  if (!gen.body?.email_otp) {
    throw new Error(`generate_link failed for ${email}: ${JSON.stringify(gen.body)}`);
  }

  const res = await call("verify-code-and-get-user", {
    email, code: gen.body.email_otp, device_id: `stress-${RUN}-${i}`, consent: true,
  });
  const token = res.body?.session?.access_token;
  const profileId = res.body?.user?.id;
  if (!token || !profileId) {
    throw new Error(`verify-code-and-get-user failed for ${email}: ${JSON.stringify(res.body)}`);
  }

  // Promote to trusted + auto_approve so the "post" k6 scenario has a
  // realistic daily quota (20/day) instead of a brand-new account's 3/day.
  await adminApi(`/rest/v1/users?id=eq.${profileId}`, {
    method: "PATCH",
    body: JSON.stringify({ trust_level: "trusted", auto_approve: true }),
  });

  return { email, profile_id: profileId, auth_user_id: authUserIdFromToken(token), token };
}

async function pool(n, worker, concurrency) {
  const results = new Array(n);
  let next = 0;
  let failed = 0;
  async function run() {
    while (next < n) {
      const i = next++;
      try {
        results[i] = await worker(i + 1);
      } catch (e) {
        failed++;
        console.error(`[${i + 1}/${n}] failed: ${e.message}`);
      }
      if ((i + 1) % 50 === 0) console.error(`... ${i + 1}/${n} (${failed} failed so far)`);
    }
  }
  await Promise.all(Array.from({ length: concurrency }, run));
  return results.filter(Boolean);
}

async function create() {
  console.error(`Creating ${N} test accounts on ${BASE} (concurrency=${CONCURRENCY})...`);
  const accounts = await pool(N, makeOne, CONCURRENCY);
  if (!accounts.length) {
    console.error("No accounts were created. Check SUPABASE_URL / SUPABASE_PROJECT_URL / ANON_KEY / SERVICE_KEY and try again.");
    process.exit(1);
  }

  fs.writeFileSync(TOKENS_FILE, JSON.stringify(accounts.map((a) => a.token), null, 2));
  fs.writeFileSync(META_FILE, JSON.stringify(accounts, null, 2));

  console.error(`Wrote ${accounts.length} tokens to ${TOKENS_FILE}`);
  console.error(`Wrote account metadata to ${META_FILE} (needed for cleanup - do not commit either file)`);
  if (accounts.length < N) {
    console.error(`Note: ${N - accounts.length} account(s) failed - see errors above. Re-run if you need exactly ${N}.`);
  }
}

async function cleanup() {
  if (!fs.existsSync(META_FILE)) {
    console.error(`No ${META_FILE} found - nothing to clean up.`);
    return;
  }
  const meta = JSON.parse(fs.readFileSync(META_FILE, "utf8"));
  console.error(`Deleting ${meta.length} stress-test account(s) from ${BASE}...`);

  let done = 0;
  for (const { profile_id, auth_user_id } of meta) {
    if (profile_id) {
      // remove any deals this token posted during the k6 "post" scenario, then the profile
      await adminApi(`/rest/v1/deals?submitted_by_user_id=eq.${profile_id}`, { method: "DELETE" });
      await adminApi(`/rest/v1/users?id=eq.${profile_id}`, { method: "DELETE" });
    }
    if (auth_user_id) {
      // uploaded test photos live in images/<authUserId>/
      const objs = await adminApi(`/storage/v1/object/list/deals`, {
        method: "POST",
        body: JSON.stringify({ prefix: `images/${auth_user_id}/`, limit: 1000 }),
      });
      const prefixes = (objs.body ?? []).map((o) => `images/${auth_user_id}/${o.name}`);
      if (prefixes.length) {
        await adminApi(`/storage/v1/object/deals`, { method: "DELETE", body: JSON.stringify({ prefixes }) });
      }
      await adminApi(`/auth/v1/admin/users/${auth_user_id}`, { method: "DELETE" });
    }
    if (++done % 50 === 0) console.error(`... ${done}/${meta.length}`);
  }

  fs.unlinkSync(META_FILE);
  if (fs.existsSync(TOKENS_FILE)) fs.unlinkSync(TOKENS_FILE);
  console.error("Done. tokens.json and tokens_meta.json removed.");
}

if (CLEANUP) {
  await cleanup();
} else {
  await create();
}
