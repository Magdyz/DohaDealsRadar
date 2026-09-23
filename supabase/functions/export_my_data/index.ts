// ============================================================================
// export_my_data
// Returns everything we store about the caller as JSON (PDPL right of access).
// ============================================================================
import { admin, requireUser } from "../_shared/auth.ts";
import { handler, ok } from "../_shared/http.ts";
import { DAY, rateLimit } from "../_shared/ratelimit.ts";

Deno.serve(handler(async (req) => {
  const caller = await requireUser(req);
  await rateLimit(`export:${caller.profile.id}`, 5, DAY, "You can download your data up to 5 times a day.");
  const db = admin();
  const id = caller.profile.id;

  const [profile, deals, votes, reports, expiry, feedback] = await Promise.all([
    db.from("users").select("id, email, username, role, trust_level, approved_deals_count, rejected_deals_count, strikes, created_at, last_login_at, consent_at").eq("id", id).single(),
    db.from("deals").select("id, title, description, link, location, governorate, category, promo_code, original_price, discounted_price, image_url, status, deletion_reason, created_at, expires_at").eq("submitted_by_user_id", id),
    db.from("votes").select("deal_id, vote_type, created_at").eq("user_id", id),
    db.from("reports").select("deal_id, reason, note, created_at").eq("reporter_user_id", id),
    db.from("deal_expiry_votes").select("deal_id, created_at").eq("user_id", id),
    db.from("feedback").select("feedback_text, email, created_at").eq("user_id", id),
  ]);

  return ok({
    data: {
      exported_at: new Date().toISOString(),
      profile: profile.data,
      deals: deals.data ?? [],
      votes: votes.data ?? [],
      reports: reports.data ?? [],
      expired_confirmations: expiry.data ?? [],
      feedback: feedback.data ?? [],
      note: "EgyptDealRadar stores only the data above. We don't use advertising IDs or third-party analytics.",
    },
  });
}));
