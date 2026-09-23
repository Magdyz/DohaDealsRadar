// ============================================================================
// EgyptDealRadar - backend end-to-end + authorization tests (real project)
//
// Creates throwaway test accounts, signs them in through the real email-code
// flow (codes generated with the admin API), exercises every user/moderator
// scenario including abuse attempts, then deletes everything it created.
//
// Usage:
//   SERVICE_KEY=<service_role key> node scripts/backend_e2e.mjs
// (the key is only read from the environment, never stored)
// Test deals are titled "[E2E] ..." so they never trigger push notifications.
// ============================================================================
import fs from "node:fs";

const props = Object.fromEntries(
  fs.readFileSync(new URL("../local.properties", import.meta.url), "utf8")
    .split(/\r?\n/).filter((l) => l.includes("=") && !l.startsWith("#"))
    .map((l) => [l.slice(0, l.indexOf("=")), l.slice(l.indexOf("=") + 1).replace(/\\/g, "")]),
);
const ANON = props.SUPABASE_ANON_KEY;
const FN = props.SUPABASE_URL.replace(/\/$/, "");
const BASE = props.SUPABASE_PUBLIC_URL.replace(/\/storage.*/, "");
const SERVICE = process.env.SERVICE_KEY;
if (!SERVICE) throw new Error("SERVICE_KEY env var required");

const RUN = Date.now().toString(36);
const results = [];
let failures = 0;
function check(name, cond, detail = "") {
  results.push(`${cond ? "PASS" : "FAIL"}  ${name}${cond ? "" : "  -> " + detail}`);
  if (!cond) failures++;
}

async function call(fn, body = {}, token = ANON, { method = "POST", query = "" } = {}) {
  const res = await fetch(`${FN}/${fn}${query}`, {
    method,
    headers: { apikey: ANON, Authorization: `Bearer ${token}`, "Content-Type": "application/json", "x-device-id": `e2e-${RUN}` },
    body: method === "GET" ? undefined : JSON.stringify(body),
  });
  let json = null;
  try { json = await res.json(); } catch { /* empty */ }
  return { status: res.status, body: json };
}

async function adminApi(path, init = {}) {
  const res = await fetch(`${BASE}${path}`, {
    ...init,
    headers: { apikey: SERVICE, Authorization: `Bearer ${SERVICE}`, "Content-Type": "application/json", ...(init.headers ?? {}) },
  });
  const text = await res.text();
  return { status: res.status, body: text ? JSON.parse(text) : null };
}

async function login(label, { consent = true } = {}) {
  const email = `e2e+${label}-${RUN}@example.com`;
  const gen = await adminApi("/auth/v1/admin/generate_link", {
    method: "POST",
    body: JSON.stringify({ type: "magiclink", email }),
  });
  if (!gen.body?.email_otp) {
    // new user: create first, then generate
    await adminApi("/auth/v1/admin/users", { method: "POST", body: JSON.stringify({ email, email_confirm: true }) });
    const again = await adminApi("/auth/v1/admin/generate_link", { method: "POST", body: JSON.stringify({ type: "magiclink", email }) });
    gen.body = again.body;
  }
  const res = await call("verify-code-and-get-user", { email, code: gen.body.email_otp, device_id: `e2e-${label}`, consent });
  return { email, res, token: res.body?.session?.access_token, refresh: res.body?.session?.refresh_token, id: res.body?.user?.id };
}

// 1x1 JPEG
const JPEG = Buffer.from("/9j/4AAQSkZJRgABAQAAAQABAAD/2wBDAAgGBgcGBQgHBwcJCQgKDBQNDAsLDBkSEw8UHRofHh0aHBwgJC4nICIsIxwcKDcpLDAxNDQ0Hyc5PTgyPC4zNDL/wAALCAABAAEBAREA/8QAFAABAAAAAAAAAAAAAAAAAAAACf/EABQQAQAAAAAAAAAAAAAAAAAAAAD/2gAIAQEAAD8AKp//2Q==", "base64");

async function upload(token) {
  const u = await call("create_upload_url", { content_type: "image/jpeg" }, token);
  if (!u.body?.upload_url) return { ok: false, u };
  const put = await fetch(u.body.upload_url, { method: "PUT", headers: { "Content-Type": "image/jpeg" }, body: JPEG });
  return { ok: put.ok, url: u.body.public_url, status: put.status };
}

const created = { emails: [] };

try {
  // ---------------------------------------------------------------- accounts
  const noConsent = await login("noconsent", { consent: false });
  check("new account without consent is refused", noConsent.res.status === 400 && noConsent.res.body?.field === "consent", JSON.stringify(noConsent.res.body));
  created.emails.push(noConsent.email);

  const A = await login("alice"); created.emails.push(A.email);
  const B = await login("bob"); created.emails.push(B.email);
  const C = await login("carol"); created.emails.push(C.email);
  check("sign-in returns a session", !!A.token && !!A.refresh && !!A.id, JSON.stringify(A.res.body));
  check("new users get an Egypt username", /^Deal/.test(A.res.body?.user?.username ?? ""), A.res.body?.user?.username);

  const wrong = await call("verify-code-and-get-user", { email: A.email, code: "000000", device_id: "x", consent: true });
  check("wrong code is rejected with INVALID_CODE", wrong.body?.code === "INVALID_CODE", JSON.stringify(wrong.body));

  // Promote Carol to admin (as an admin would via SQL/dashboard)
  await adminApi(`/rest/v1/users?id=eq.${C.id}`, { method: "PATCH", body: JSON.stringify({ role: "admin", auto_approve: true }) });

  // ---------------------------------------------------------------- uploads
  const upA = await upload(A.token);
  check("signed upload works for a logged-in user", upA.ok, JSON.stringify(upA));
  const anonUp = await call("create_upload_url", {}, ANON);
  check("upload URL refused without login", anonUp.status === 401, anonUp.status);

  // ---------------------------------------------------------------- posting
  const link = `https://www.amazon.eg/dp/B0E2E${RUN.slice(0, 5).toUpperCase().padEnd(5, "X")}?tag=aff-21&utm_source=x`;
  const deal1 = await call("submit_deal", {
    title: `[E2E] Galaxy A55 256GB ${RUN}`, description: "Test deal", link, category: "electronics",
    governorate: "cairo", original_price: 24999, discounted_price: 19999, image_url: upA.url, expires_in_days: 5,
    posted_by: "Hacker", user_id: C.id,
  }, A.token);
  check("regular user's deal goes to review", deal1.body?.status === "pending", JSON.stringify(deal1.body));
  const d1 = deal1.body?.data?.[0];
  check("username comes from the account, not the request", d1?.posted_by === A.res.body.user.username, d1?.posted_by);
  check("private columns not returned to the poster", d1 && !("submitted_by_device" in d1) && !("submitted_by_user_id" in d1), JSON.stringify(Object.keys(d1 ?? {})));

  const foreignImg = await call("submit_deal", {
    title: `[E2E] Stolen image ${RUN}`, link: `https://www.jumia.com.eg/x-${RUN}.html`, category: "other", image_url: upA.url,
  }, B.token);
  check("can't post with someone else's photo", foreignImg.body?.field === "image", JSON.stringify(foreignImg.body));

  const upB = await upload(B.token);
  const short = await call("submit_deal", { title: `[E2E] Short link ${RUN}`, link: "https://bit.ly/abc", image_url: upB.url }, B.token);
  check("shortened links are blocked", short.body?.code === "LINK_BLOCKED", JSON.stringify(short.body));
  const http = await call("submit_deal", { title: `[E2E] Http link ${RUN}`, link: "http://example.com/x", image_url: upB.url }, B.token);
  check("non-https links are blocked", http.body?.code === "LINK_BLOCKED", JSON.stringify(http.body));
  const price = await call("submit_deal", { title: `[E2E] Bad price ${RUN}`, link: `https://noon.com/egypt-en/x-${RUN}/p/`, image_url: upB.url, original_price: 100, discounted_price: 150 }, B.token);
  check("discount higher than original is rejected", price.body?.field === "discounted_price", JSON.stringify(price.body));

  // ---------------------------------------------------------------- moderation authz
  const forged = await call("approve_deal", { deal_id: d1?.id, moderator_user_id: C.id }, B.token);
  check("regular user can't approve (even naming an admin)", forged.status === 403, JSON.stringify(forged.body));
  const anonApprove = await call("approve_deal", { deal_id: d1?.id }, ANON);
  check("anonymous can't approve", anonApprove.status === 401, anonApprove.status);
  const pendingList = await call("get_pending_deals", {}, C.token);
  check("admin sees the pending deal", (pendingList.body?.data ?? []).some((d) => d.id === d1?.id), JSON.stringify(pendingList.body).slice(0, 200));
  const approve = await call("approve_deal", { deal_id: d1?.id }, C.token);
  check("admin can approve", approve.body?.success === true && approve.body?.data?.status === "approved", JSON.stringify(approve.body));

  // ---------------------------------------------------------------- feed
  const feed = await call("get_deals", {}, ANON, { method: "GET", query: "?sort_by=newest&limit=50" });
  const inFeed = (feed.body?.data ?? []).find((d) => d.id === d1?.id);
  check("approved deal appears in the public feed", !!inFeed, JSON.stringify(feed.body).slice(0, 200));
  check("feed hides private columns", inFeed && !("submitted_by_device" in inFeed) && !("approved_by" in inFeed), JSON.stringify(Object.keys(inFeed ?? {})));
  const cairo = await call("get_deals", {}, ANON, { method: "GET", query: "?governorate=cairo&limit=50" });
  const alex = await call("get_deals", {}, ANON, { method: "GET", query: "?governorate=alexandria&limit=50" });
  check("governorate filter includes Cairo deal", (cairo.body?.data ?? []).some((d) => d.id === d1?.id));
  check("governorate filter excludes it for Alexandria", !(alex.body?.data ?? []).some((d) => d.id === d1?.id));
  const search = await call("get_deals", {}, ANON, { method: "GET", query: `?q=${encodeURIComponent("galaxy a55")}&limit=50` });
  check("search finds the deal", (search.body?.data ?? []).some((d) => d.id === d1?.id), JSON.stringify(search.body).slice(0, 200));
  const page1 = await call("get_deals", {}, ANON, { method: "GET", query: "?sort_by=newest&limit=1" });
  check("cursor pagination returns a next_cursor", page1.body?.pagination && "next_cursor" in page1.body.pagination, JSON.stringify(page1.body?.pagination));

  // ---------------------------------------------------------------- votes
  const own = await call("cast_vote", { deal_id: d1?.id, vote_type: "hot" }, A.token);
  check("can't vote on your own deal", own.body?.code === "OWN_DEAL", JSON.stringify(own.body));
  const v1 = await call("cast_vote", { deal_id: d1?.id, vote_type: "hot", user_email: A.email, user_id: A.id }, B.token);
  check("vote is recorded for the caller (forged identity ignored)", v1.body?.user_vote === "hot" && v1.body?.data?.hot_count === 1, JSON.stringify(v1.body));
  const v2 = await call("cast_vote", { deal_id: d1?.id, vote_type: "cold" }, B.token);
  check("switching vote works", v2.body?.user_vote === "cold" && v2.body?.data?.hot_count === 0 && v2.body?.data?.cold_count === 1, JSON.stringify(v2.body));
  const v3 = await call("cast_vote", { deal_id: d1?.id, vote_type: "cold" }, B.token);
  check("same vote again removes it", v3.body?.user_vote === null && v3.body?.data?.cold_count === 0, JSON.stringify(v3.body));
  const anonVote = await call("cast_vote", { deal_id: d1?.id, vote_type: "hot", device_id: "x" }, ANON);
  check("anonymous voting refused", anonVote.status === 401, anonVote.status);
  await call("cast_vote", { deal_id: d1?.id, vote_type: "hot" }, B.token);
  const feedB = await call("get_deals", {}, B.token, { method: "GET", query: "?sort_by=newest&limit=50" });
  check("feed tells a user their own vote", (feedB.body?.data ?? []).find((d) => d.id === d1?.id)?.user_vote === "hot");

  // ---------------------------------------------------------------- duplicates
  const upB2 = await upload(B.token);
  const dupUrl = link.replace("?tag=aff-21&utm_source=x", "?ref=share");
  const dup = await call("submit_deal", { title: `[E2E] Samsung phone offer ${RUN}`, link: dupUrl, category: "electronics", image_url: upB2.url }, B.token);
  check("same product link (different tracking) is a DUPLICATE_DEAL", dup.body?.code === "DUPLICATE_DEAL" && dup.body?.existing?.id === d1?.id, JSON.stringify(dup.body));
  const similarTitle = `[E2E] Galaxy A55 256GB ${RUN} offer`;
  const sim = await call("submit_deal", { title: similarTitle, link: `https://www.btech.com/en/galaxy-${RUN}`, category: "electronics", image_url: upB2.url }, B.token);
  check("similar title asks for confirmation (POSSIBLE_DUPLICATE)", sim.body?.code === "POSSIBLE_DUPLICATE", JSON.stringify(sim.body));
  const pre = await call("check_duplicate", { link: dupUrl, title: similarTitle }, B.token);
  check("check_duplicate reports a blocking match", pre.body?.blocking === true, JSON.stringify(pre.body));
  const confirmed = await call("submit_deal", { title: similarTitle, link: `https://www.btech.com/en/galaxy-${RUN}`, category: "electronics", image_url: upB2.url, confirm_not_duplicate: true }, B.token);
  check("user can confirm it's different", confirmed.body?.success === true, JSON.stringify(confirmed.body));

  // ---------------------------------------------------------------- reports
  const shortNote = await call("create_report", { deal_id: d1?.id, reason: "scam", note: "bad" }, B.token);
  check("scam report needs details", shortNote.body?.field === "note", JSON.stringify(shortNote.body));
  const rep = await call("create_report", { deal_id: d1?.id, reason: "expired", note: "It ended yesterday" }, B.token);
  check("report accepted", rep.body?.success === true, JSON.stringify(rep.body));
  const rep2 = await call("create_report", { deal_id: d1?.id, reason: "expired" }, B.token);
  check("same user can't report twice", rep2.body?.code === "ALREADY_DONE", JSON.stringify(rep2.body));
  const ownRep = await call("create_report", { deal_id: d1?.id, reason: "other" }, A.token);
  check("can't report your own deal", ownRep.body?.code === "OWN_DEAL", JSON.stringify(ownRep.body));
  const reports = await call("get_reports", {}, C.token);
  const r = (reports.body?.data ?? []).find((x) => x.deal_id === d1?.id);
  check("admin sees report with masked reporter email", !!r && /\*\*\*@/.test(r.reporter_email ?? ""), JSON.stringify(r));
  const repAsUser = await call("get_reports", {}, B.token);
  check("regular user can't list reports", repAsUser.status === 403, repAsUser.status);

  // ---------------------------------------------------------------- expiry
  const exp = await call("mark_expired", { deal_id: d1?.id }, B.token);
  check("expired confirmation counted", exp.body?.expired_votes === 1 && exp.body?.archived === false, JSON.stringify(exp.body));
  const exp2 = await call("mark_expired", { deal_id: d1?.id }, B.token);
  check("expired confirmation only once per user", exp2.body?.code === "ALREADY_DONE", JSON.stringify(exp2.body));

  // ---------------------------------------------------------------- ownership
  const swap = await call("update-deal-image", { deal_id: d1?.id, image_url: upB2.url }, B.token);
  check("can't change someone else's deal photo", swap.status === 403 || swap.body?.field === "image", JSON.stringify(swap.body));
  const del = await call("delete_deal", { deal_id: d1?.id }, B.token);
  check("can't delete someone else's deal", del.status === 403, JSON.stringify(del.body));
  const role = await call("update_user_role", { target_user_id: B.id, new_role: "admin", admin_user_id: C.id }, B.token);
  check("regular user can't grant roles", role.status === 403, JSON.stringify(role.body));
  const profAB = await call("get_user_profile", { user_id: A.id }, B.token);
  check("other users' profiles hide email", profAB.body?.data && !profAB.body.data.email, JSON.stringify(profAB.body));
  const profSelf = await call("get_user_profile", {}, A.token);
  check("own profile shows email", profSelf.body?.data?.email === A.email, JSON.stringify(profSelf.body));
  const stats = await call("get_stats", {}, C.token);
  check("admin stats available", typeof stats.body?.data?.live_deals === "number", JSON.stringify(stats.body).slice(0, 200));
  const statsUser = await call("get_stats", {}, B.token);
  check("stats refused for regular users", statsUser.status === 403, statsUser.status);

  // ---------------------------------------------------------------- rate limits
  let limited = null;
  for (let i = 0; i < 4 && !limited; i++) {
    const up = await upload(A.token);
    const r = await call("submit_deal", { title: `[E2E] Rate test ${i} ${RUN} ${Math.random()}`, link: `https://www.jumia.com.eg/rate-${RUN}-${i}.html`, image_url: up.url, category: "other" }, A.token);
    if (r.body?.code === "RATE_LIMITED") limited = r;
  }
  check("new accounts are limited to 3 posts per day", limited && limited.body.retry_after > 0, JSON.stringify(limited?.body));

  // ---------------------------------------------------------------- session / tokens
  const garbage = await call("cast_vote", { deal_id: d1?.id, vote_type: "hot" }, "eyJhbGciOiJIUzI1NiJ9.eyJzdWIiOiJ4Iiwicm9sZSI6ImF1dGhlbnRpY2F0ZWQifQ.bad");
  check("forged token is rejected", garbage.status === 401, garbage.status);
  const refreshed = await fetch(`${BASE}/auth/v1/token?grant_type=refresh_token`, {
    method: "POST", headers: { apikey: ANON, "Content-Type": "application/json" }, body: JSON.stringify({ refresh_token: A.refresh }),
  }).then((r) => r.json());
  check("refresh token gives a new session", !!refreshed.access_token, JSON.stringify(refreshed).slice(0, 120));

  // ---------------------------------------------------------------- privacy rights
  const exportA = await call("export_my_data", {}, A.token);
  check("data export works", exportA.body?.data?.profile?.email === A.email && Array.isArray(exportA.body?.data?.deals), JSON.stringify(exportA.body).slice(0, 200));
  const noConfirm = await call("delete_account", {}, B.token);
  check("delete account needs confirmation", noConfirm.body?.code === "VALIDATION", JSON.stringify(noConfirm.body));
  const delB = await call("delete_account", { confirm: "DELETE" }, B.token);
  check("user can delete their account", delB.body?.success === true, JSON.stringify(delB.body));
  const afterDel = await call("cast_vote", { deal_id: d1?.id, vote_type: "hot" }, B.token);
  check("deleted account's session no longer works", afterDel.status === 401, afterDel.status);
  const delAdmin = await call("delete_account", { confirm: "DELETE" }, C.token);
  check("admin can't delete own account from the app", delAdmin.status === 403, JSON.stringify(delAdmin.body));
  const delA = await call("delete_account", { confirm: "DELETE" }, A.token);
  check("poster can delete account (deals removed)", delA.body?.success === true, JSON.stringify(delA.body));

  // link preview (best effort, network dependent)
  const Z = await login("zed"); created.emails.push(Z.email);
  const prev = await call("link_preview", { link: "https://www.amazon.eg/-/en/dp/B0CRJWGG2V" }, Z.token);
  check("link preview responds", prev.body?.success === true && prev.body?.preview?.store === "Amazon.eg", JSON.stringify(prev.body).slice(0, 200));
  const ssrf = await call("link_preview", { link: "https://169.254.169.254/latest/meta-data" }, Z.token);
  check("link preview blocks internal addresses", ssrf.body?.code === "LINK_BLOCKED", JSON.stringify(ssrf.body));
} finally {
  // ---------------------------------------------------------------- cleanup
  for (const email of created.emails) {
    const u = await adminApi(`/rest/v1/users?email=eq.${encodeURIComponent(email)}&select=id,auth_user_id`);
    for (const row of u.body ?? []) {
      await adminApi(`/rest/v1/deals?submitted_by_user_id=eq.${row.id}`, { method: "DELETE" });
      await adminApi(`/rest/v1/users?id=eq.${row.id}`, { method: "DELETE" });
    }
    const list = await adminApi(`/auth/v1/admin/users?per_page=200`);
    for (const au of list.body?.users ?? []) {
      if (au.email !== email) continue;
      // uploaded test photos live in images/<authUserId>/
      const objs = await adminApi(`/storage/v1/object/list/deals`, { method: "POST", body: JSON.stringify({ prefix: `images/${au.id}/`, limit: 1000 }) });
      const prefixes = (objs.body ?? []).map((o) => `images/${au.id}/${o.name}`);
      if (prefixes.length) await adminApi(`/storage/v1/object/deals`, { method: "DELETE", body: JSON.stringify({ prefixes }) });
      await adminApi(`/auth/v1/admin/users/${au.id}`, { method: "DELETE" });
    }
  }
  await adminApi(`/rest/v1/rate_limits?key=like.*e2e*`, { method: "DELETE" });
  const leftovers = await adminApi(`/rest/v1/deals?title=like.%5BE2E%5D*&select=id`);
  for (const d of leftovers.body ?? []) await adminApi(`/rest/v1/deals?id=eq.${d.id}`, { method: "DELETE" });

  console.log(results.join("\n"));
  console.log(`\n${results.length - failures}/${results.length} passed`);
  process.exitCode = failures ? 1 : 0;
}
