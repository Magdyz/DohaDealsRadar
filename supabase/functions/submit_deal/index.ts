// ============================================================================
// submit_deal
// Requires a logged-in user. Everything is validated server-side:
//   * identity + username come from the session (never from the body)
//   * rate limits by trust level
//   * link safety (https, no shorteners, optional Safe Browsing)
//   * price sanity, field lengths, category/governorate
//   * photo must be the caller's own upload
//   * duplicates: same link -> DUPLICATE_DEAL; similar title ->
//     POSSIBLE_DUPLICATE unless the user confirms (confirm_not_duplicate)
// Approval: staff -> live; trusted users -> live unless something looks risky
// (unknown store, big discount, integrity failure, 10% spot check).
// ============================================================================
import { admin, isStaff, requireUser } from "../_shared/auth.ts";
import { ApiError, background, handler, ok, readJson, str } from "../_shared/http.ts";
import {
  assertOwnImageUrl, CATEGORIES, canonicalizeUrl, GOVERNORATES, hostOf, isUnsafeUrl, normalizeTitle,
  parsePrice, PUBLIC_DEAL_COLUMNS, storeForHost, validateLink,
} from "../_shared/deals.ts";
import { DAY, HOUR, rateLimit } from "../_shared/ratelimit.ts";
import { checkIntegrity } from "../_shared/integrity.ts";
import { notifyNewDeal } from "../_shared/fcm.ts";

Deno.serve(handler(async (req) => {
  const caller = await requireUser(req);
  const p = caller.profile;
  const staff = isStaff(caller);
  const body = await readJson(req);

  // ---- attempt limit (stops brute-forcing the validators) ----------------
  await rateLimit(`post-attempt:${p.id}`, staff ? 500 : 40, HOUR, "Too many attempts. Please wait a bit and try again.");

  // ---- fields ------------------------------------------------------------
  const title = str(body.title, 300);
  if (!title || title.length < 5) throw new ApiError("VALIDATION", "Please enter a title (at least 5 characters).", { field: "title" });
  if (title.length > 150) throw new ApiError("VALIDATION", "The title is too long (max 150 characters).", { field: "title" });
  if (/https?:\/\/|www\./i.test(title)) throw new ApiError("VALIDATION", "Please don't put links in the title.", { field: "title" });

  const description = str(body.description, 2500);
  if (description && description.length > 2000) throw new ApiError("VALIDATION", "The description is too long (max 2000 characters).", { field: "description" });

  const category = CATEGORIES.includes(body.category) ? body.category : "other";
  const rawLink = str(body.link, 2100);
  const location = str(body.location, 300);
  if (!rawLink && !location) throw new ApiError("VALIDATION", "Add a link for online deals or a location for in-store deals.", { field: "link" });
  if (location && (location.length < 3 || location.length > 200)) throw new ApiError("VALIDATION", "Please enter a valid location.", { field: "location" });

  const governorate = GOVERNORATES.includes(body.governorate) ? body.governorate : "all_egypt";
  const promoCode = str(body.promo_code, 60);
  if (promoCode && promoCode.length > 50) throw new ApiError("VALIDATION", "Promo code is too long.", { field: "promo_code" });

  const expiresInDays = Math.max(1, Math.min(30, Number(body.expires_in_days) || 10));

  const originalPrice = parsePrice(body.original_price, "original_price");
  const discountedPrice = parsePrice(body.discounted_price, "discounted_price");
  if (originalPrice && discountedPrice && discountedPrice >= originalPrice) {
    throw new ApiError("VALIDATION", "The discounted price must be lower than the original price.", { field: "discounted_price" });
  }
  const bigDiscount = !!(originalPrice && discountedPrice && discountedPrice / originalPrice < 0.1);

  const imageUrl = str(body.image_url, 500);
  if (!imageUrl) throw new ApiError("VALIDATION", "Please add a photo of the deal.", { field: "image" });
  assertOwnImageUrl(imageUrl, caller.authUserId);
  const imageHash = typeof body.image_hash === "string" && /^-?\d{1,20}$/.test(body.image_hash) ? body.image_hash : null;

  // ---- link safety -------------------------------------------------------
  let link: string | null = null;
  let canonicalUrl: string | null = null;
  let store: string | null = str(body.store, 60);
  let knownStore = false;
  if (rawLink) {
    const url = validateLink(rawLink);
    if (await isUnsafeUrl(url.toString())) throw new ApiError("LINK_BLOCKED", "This link can't be posted for safety reasons.", { reason: "unsafe" });
    link = url.toString();
    canonicalUrl = canonicalizeUrl(url);
    const known = storeForHost(hostOf(url));
    knownStore = !!known;
    store = known ?? store;
  }

  // ---- duplicates --------------------------------------------------------
  const titleNorm = normalizeTitle(title);
  const { data: similar, error: simError } = await admin().rpc("find_similar_deals", {
    p_canonical_url: canonicalUrl,
    p_title_norm: titleNorm,
    p_image_hash: imageHash,
    p_exclude_id: null,
  });
  if (simError) throw simError;
  const matches = (similar ?? []) as Array<{ id: string; title: string; image_url: string; match_type: string; score: number }>;
  const hard = matches.find((m) => m.match_type === "url");
  if (hard) {
    throw new ApiError("DUPLICATE_DEAL", "This deal is already posted. You can vote on it instead.", { existing: hard });
  }
  if (matches.length && body.confirm_not_duplicate !== true) {
    throw new ApiError("POSSIBLE_DUPLICATE", "Similar deals are already posted. Is yours different?", { similar: matches });
  }

  // ---- daily post limit (only valid posts count) -------------------------
  const accountAgeHours = (Date.now() - new Date(p.created_at).getTime()) / 3_600_000;
  const dailyLimit = staff ? 200 : p.auto_approve ? 20 : accountAgeHours < 24 ? 3 : 5;
  await rateLimit(`post:${p.id}`, dailyLimit, DAY, `You can post up to ${dailyLimit} deals per day. Please try again tomorrow.`, { limit: dailyLimit });

  // ---- integrity & approval ---------------------------------------------
  const integrity = await checkIntegrity(req, "submit_deal");
  let status: "approved" | "pending" = "pending";
  if (staff) status = "approved";
  else if (p.auto_approve && integrity !== "risky" && !bigDiscount && (knownStore || !rawLink) && Math.random() >= 0.1) {
    status = "approved";
  }

  const expiresAt = new Date(Date.now() + expiresInDays * 86400_000).toISOString();
  const now = new Date().toISOString();
  const deviceId = str(req.headers.get("x-device-id") ?? body.device_id, 100);

  const { data, error } = await admin().from("deals").insert({
    title,
    title_norm: titleNorm,
    description,
    link,
    canonical_url: canonicalUrl,
    image_url: imageUrl,
    image_hash: imageHash,
    location,
    governorate,
    store,
    category,
    promo_code: promoCode,
    posted_by: p.username,
    expires_at: expiresAt,
    original_price: originalPrice,
    discounted_price: discountedPrice,
    status,
    auto_approved: status === "approved",
    requires_review: status !== "approved",
    hot_count: 0,
    cold_count: 0,
    submitted_by_user_id: p.id,
    submitted_by_device: deviceId,
    ...(status === "approved" ? { approved_at: now, approved_by: p.id } : {}),
  }).select(PUBLIC_DEAL_COLUMNS);

  if (error) {
    console.error("insert deal failed:", error.message);
    throw new ApiError("SERVER_ERROR", "We couldn't post your deal. Please try again.");
  }

  const deal = (data as any[])[0];
  if (status === "approved") {
    background(notifyNewDeal({ id: deal.id, title: deal.title, category: deal.category, image_url: deal.image_url }));
  }

  return ok({
    message: status === "approved" ? "Deal posted" : "Deal submitted for review",
    status,
    data,
  });
}));
