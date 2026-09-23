// ============================================================================
// EgyptDealRadar - scripts/stress/k6_feed_vote_post.js
//
// Load test against the real Edge Functions (get_deals, cast_vote,
// create_upload_url, submit_deal, get_user_deals). Request shapes are taken
// straight from supabase/functions/{get_deals,cast_vote,create_upload_url,
// submit_deal,get_user_deals}/index.ts - see scripts/stress/README.md for
// how this maps to each function.
//
// Scenarios:
//   main (ramping 10 -> 500 VUs): each iteration randomly does one of
//     - browse   (80%) get_deals page 1 for a random category/governorate/
//                sort (20% of browses add a search `q`), then up to 2
//                "load more" pages via `pagination.next_cursor`
//     - vote     (10%) get_deals hottest page, then cast_vote on a random
//                deal from it
//     - post      (5%) create_upload_url -> PUT a tiny jpeg -> submit_deal
//     - account   (5%) get_user_deals page 1 (the caller's own deals)
//   hot_deal (constant 200 VUs, only if HOT_DEAL_ID is set): every VU
//     repeatedly casts a vote on the SAME deal id, to exercise the
//     toggle_vote() row lock / hot-row contention path.
//
// Config (env vars):
//   SUPABASE_URL   required. Functions base, e.g.
//                  https://<staging-ref>.functions.supabase.co
//                  (same shape as SUPABASE_URL in local.properties / the FN
//                  constant in scripts/backend_e2e.mjs)
//   ANON_KEY       required. The staging project's anon key.
//   TOKENS_FILE    required. Path to a JSON array of user access tokens,
//                  one per VU (see scripts/stress/make_tokens.mjs). VUs
//                  pick tokens round-robin so no single account gets hit
//                  by every VU (which would just measure the per-user rate
//                  limiter, not backend capacity).
//   HOT_DEAL_ID    optional. A real, approved deal id on staging. Enables
//                  the hot_deal scenario. Pick one e.g. with:
//                  select id from deals where status='approved' and is_archived=false limit 1;
//
// Run (PowerShell):
//   $env:SUPABASE_URL="https://<ref>.functions.supabase.co"; $env:ANON_KEY="..."; $env:TOKENS_FILE="scripts/stress/tokens.json"; $env:HOT_DEAL_ID="<uuid>"
//   k6 run scripts/stress/k6_feed_vote_post.js
//
// Run (bash):
//   SUPABASE_URL=https://<ref>.functions.supabase.co ANON_KEY=... TOKENS_FILE=scripts/stress/tokens.json HOT_DEAL_ID=<uuid> \
//   k6 run scripts/stress/k6_feed_vote_post.js
//
// SAFETY: refuses to run at all if SUPABASE_URL contains the production
// project ref (nzchbnshkrkdqpcawohu, taken from local.properties /
// supabase/migrations/20260922_security_hardening.sql).
// ============================================================================
import http from "k6/http";
import encoding from "k6/encoding";
import exec from "k6/execution";
import { check, sleep } from "k6";
import { SharedArray } from "k6/data";

// ---------------------------------------------------------------------------
// Config + safety guard (runs in the init context, once per VU, before any
// scenario iteration - so this aborts the whole run before a single request
// is made if it doesn't look like a staging project).
// ---------------------------------------------------------------------------
const PROD_REF = "nzchbnshkrkdqpcawohu";
const SUPABASE_URL = (__ENV.SUPABASE_URL || "").replace(/\/$/, "");
const ANON_KEY = __ENV.ANON_KEY || "";
const TOKENS_FILE = __ENV.TOKENS_FILE || "";
const HOT_DEAL_ID = __ENV.HOT_DEAL_ID || "";

if (!SUPABASE_URL || !ANON_KEY || !TOKENS_FILE) {
  throw new Error(
    "Missing required env vars. Need SUPABASE_URL, ANON_KEY, TOKENS_FILE " +
    "(see the header comment of this file for examples).",
  );
}
if (SUPABASE_URL.includes(PROD_REF)) {
  throw new Error(
    `Refusing to run: SUPABASE_URL ("${SUPABASE_URL}") contains the PRODUCTION ` +
    `project ref (${PROD_REF}). Point this at a STAGING project.`,
  );
}
if (!HOT_DEAL_ID) {
  console.warn("HOT_DEAL_ID not set - the hot_deal scenario (200 VUs voting on one deal) is disabled.");
}

const tokens = new SharedArray("tokens", function () {
  const parsed = JSON.parse(open(TOKENS_FILE));
  if (!Array.isArray(parsed) || parsed.length === 0) {
    throw new Error(`${TOKENS_FILE} must contain a non-empty JSON array of access tokens.`);
  }
  return parsed;
});

// Only network errors / status 0 / 5xx count as a "failed" request for the
// http_req_failed metric and its <1% threshold. Business-rule 4xx responses
// (RATE_LIMITED, OWN_DEAL, DUPLICATE_DEAL, DEAL_UNAVAILABLE, ...) are
// expected under load and are asserted individually with check() instead -
// they should not blow the infra error-rate budget.
http.setResponseCallback(http.expectedStatuses({ min: 200, max: 499 }));

// Keep in sync with supabase/functions/_shared/deals.ts
const CATEGORIES = [
  "food_dining", "groceries", "electronics", "shopping_fashion",
  "telecom", "entertainment", "home_services", "other",
];
const GOVERNORATES = [
  "all_egypt", "cairo", "giza", "alexandria", "qalyubia", "sharqia", "dakahlia", "gharbia",
  "monufia", "beheira", "kafr_el_sheikh", "damietta", "port_said", "ismailia", "suez",
  "fayoum", "beni_suef", "minya", "asyut", "sohag", "qena", "luxor", "aswan",
  "red_sea", "new_valley", "matrouh", "north_sinai", "south_sinai",
];
const SORTS = ["hottest", "newest", "top_week"];
const SEARCH_TERMS = ["phone", "laptop", "shoes", "discount", "offer", "tv", "sale", "عرض", "خصم", "موبايل"];
const STORE_HOSTS = ["amazon.eg", "noon.com", "jumia.com.eg", "btech.com", "carrefouregypt.com"];

// 1x1 JPEG (same fixture scripts/backend_e2e.mjs uses), decoded once.
const JPEG_B64 =
  "/9j/4AAQSkZJRgABAQAAAQABAAD/2wBDAAgGBgcGBQgHBwcJCQgKDBQNDAsLDBkSEw8UHRofHh0aHBwgJC4nICIsIxwcKDcpLDAxNDQ0Hyc5PTgyPC4zNDL/wAALCAABAAEBAREA/8QAFAABAAAAAAAAAAAAAAAAAAAACf/EABQQAQAAAAAAAAAAAAAAAAAAAAD/2gAIAQEAAD8AKp//2Q==";
const JPEG_BYTES = encoding.b64decode(JPEG_B64);

// ---------------------------------------------------------------------------
// Scenarios
// ---------------------------------------------------------------------------
const scenarios = {
  main: {
    executor: "ramping-vus",
    exec: "mainFlow",
    startVUs: 0,
    stages: [
      { duration: "2m", target: 10 },
      { duration: "3m", target: 100 },
      { duration: "5m", target: 300 },
      { duration: "5m", target: 500 },
      { duration: "5m", target: 500 },
      { duration: "2m", target: 0 },
    ],
    gracefulRampDown: "30s",
  },
};

if (HOT_DEAL_ID) {
  scenarios.hot_deal = {
    executor: "ramping-vus",
    exec: "hotDealFlow",
    startVUs: 0,
    startTime: "2m", // let `main` warm the backend up first
    stages: [
      { duration: "30s", target: 200 },
      { duration: "3m", target: 200 },
      { duration: "30s", target: 0 },
    ],
    gracefulRampDown: "15s",
  };
}

export const options = {
  scenarios,
  thresholds: {
    http_req_failed: ["rate<0.01"],
    "http_req_duration{name:get_deals}": ["p(95)<300"],
    "http_req_duration{name:cast_vote}": ["p(95)<400"],
    "http_req_duration{name:submit_deal}": ["p(95)<1500"],
  },
};

// ---------------------------------------------------------------------------
// Helpers
// ---------------------------------------------------------------------------
function myToken() {
  return tokens[(exec.vu.idInTest - 1) % tokens.length];
}

function authHeaders(token) {
  return {
    apikey: ANON_KEY,
    Authorization: `Bearer ${token}`,
    "Content-Type": "application/json",
    "x-device-id": `k6-vu-${exec.vu.idInTest}`,
  };
}

function pick(arr) {
  return arr[Math.floor(Math.random() * arr.length)];
}

function safeJson(res) {
  try { return res.json(); } catch { return null; }
}

function thinkTime() {
  sleep(1 + Math.random() * 2);
}

function getDeals(token, qs) {
  const res = http.get(`${SUPABASE_URL}/get_deals?${qs}`, { headers: authHeaders(token), tags: { name: "get_deals" } });
  check(res, { "get_deals 200": (r) => r.status === 200 });
  return res;
}

// ---------------------------------------------------------------------------
// browse (80% of main): page 1 + up to 2 "load more" pages via next_cursor
// ---------------------------------------------------------------------------
function browse(token) {
  const params = new URLSearchParams();
  params.set("sort_by", pick(SORTS));
  if (Math.random() < 0.5) params.set("category", pick(CATEGORIES));
  if (Math.random() < 0.5) params.set("governorate", pick(GOVERNORATES));
  if (Math.random() < 0.2) params.set("q", pick(SEARCH_TERMS));
  params.set("limit", "20");

  let res = getDeals(token, params.toString());
  let body = safeJson(res);
  let cursor = body?.pagination?.next_cursor;

  for (let page = 0; page < 2 && cursor; page++) {
    const p = new URLSearchParams(params);
    p.set("cursor", cursor);
    res = getDeals(token, p.toString());
    body = safeJson(res);
    cursor = body?.pagination?.next_cursor;
  }
}

// ---------------------------------------------------------------------------
// vote (10% of main): browse hottest, then cast_vote on a random result
// ---------------------------------------------------------------------------
function vote(token) {
  const res = getDeals(token, new URLSearchParams({ sort_by: "hottest", limit: "20" }).toString());
  const deals = safeJson(res)?.data ?? [];
  if (!deals.length) return;

  const deal = pick(deals);
  const voteType = Math.random() < 0.6 ? "hot" : "cold";
  const r = http.post(
    `${SUPABASE_URL}/cast_vote`,
    JSON.stringify({ deal_id: deal.id, vote_type: voteType }),
    { headers: authHeaders(token), tags: { name: "cast_vote" } },
  );
  check(r, {
    // 200 = voted; 400/409/410 = expected app-level rejections (own deal,
    // already voted that way, deal no longer live); 429 = rate limited.
    "cast_vote handled": (resp) => [200, 400, 409, 410, 429].includes(resp.status),
  });
}

// ---------------------------------------------------------------------------
// post (5% of main): create_upload_url -> PUT jpeg -> submit_deal
// ---------------------------------------------------------------------------
function post(token) {
  const up = http.post(
    `${SUPABASE_URL}/create_upload_url`,
    JSON.stringify({ content_type: "image/jpeg" }),
    { headers: authHeaders(token), tags: { name: "create_upload_url" } },
  );
  const upBody = safeJson(up);
  if (up.status !== 200 || !upBody?.upload_url) {
    check(up, { "create_upload_url ok": () => false });
    return;
  }

  const put = http.put(upBody.upload_url, JPEG_BYTES, {
    headers: { "Content-Type": "image/jpeg" },
    tags: { name: "storage_put" },
  });
  if (!check(put, { "photo uploaded": (r) => r.status >= 200 && r.status < 300 })) return;

  const tag = `${exec.vu.idInTest}-${exec.scenario.iterationInTest}-${Date.now()}`;
  const payload = {
    title: `[LOADTEST] k6 deal ${tag}`,
    description: `Generated by k6 load test. ${pick(SEARCH_TERMS)} تم إنشاؤه بواسطة اختبار الحمل.`,
    category: pick(CATEGORIES),
    governorate: Math.random() < 0.7 ? pick(GOVERNORATES) : "all_egypt",
    link: `https://www.${pick(STORE_HOSTS)}/k6-${tag}`,
    original_price: 100 + Math.floor(Math.random() * 900),
    discounted_price: 50 + Math.floor(Math.random() * 40), // always < original_price above
    image_url: upBody.public_url,
    expires_in_days: 7,
  };
  const sub = http.post(`${SUPABASE_URL}/submit_deal`, JSON.stringify(payload), {
    headers: authHeaders(token),
    tags: { name: "submit_deal" },
  });
  check(sub, {
    // 200 = posted (pending or approved); 409 = duplicate; 429 = rate limited.
    "submit_deal handled": (r) => [200, 409, 429].includes(r.status),
  });
}

// ---------------------------------------------------------------------------
// account (5% of main): the caller's own deals, page 1
// ---------------------------------------------------------------------------
function account(token) {
  const r = http.post(`${SUPABASE_URL}/get_user_deals`, JSON.stringify({ page: 1, limit: 20 }), {
    headers: authHeaders(token),
    tags: { name: "get_user_deals" },
  });
  check(r, { "get_user_deals 200": (resp) => resp.status === 200 });
}

// ---------------------------------------------------------------------------
// Exported VU functions
// ---------------------------------------------------------------------------
export function mainFlow() {
  const token = myToken();
  const r = Math.random();
  if (r < 0.80) browse(token);
  else if (r < 0.90) vote(token);
  else if (r < 0.95) post(token);
  else account(token);
  thinkTime();
}

export function hotDealFlow() {
  const token = myToken();
  const voteType = Math.random() < 0.6 ? "hot" : "cold";
  const r = http.post(
    `${SUPABASE_URL}/cast_vote`,
    JSON.stringify({ deal_id: HOT_DEAL_ID, vote_type: voteType }),
    { headers: authHeaders(token), tags: { name: "cast_vote" } },
  );
  check(r, { "hot deal vote handled": (resp) => [200, 400, 409, 410, 429].includes(resp.status) });
  sleep(0.5 + Math.random() * 1.5);
}
