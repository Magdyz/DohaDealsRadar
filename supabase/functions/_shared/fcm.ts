// ============================================================================
// Firebase Cloud Messaging (HTTP v1) helpers
//
// Topics (the app subscribes according to its language):
//   all_deals / all_deals_ar              - every new approved deal
//   cat_<category> / cat_<category>_ar    - new deals in a category
//   user_<profileId> / user_<profileId>_ar - personal updates (deal approved/rejected)
// ============================================================================
import { googleAccessToken } from "./google.ts";

const CATEGORY_EMOJI: Record<string, string> = {
  food_dining: "🍔", groceries: "🛒", electronics: "📱", shopping_fashion: "🛍️",
  telecom: "📶", entertainment: "🎮", home_services: "🏠", other: "⭐",
};
const CATEGORY_EN: Record<string, string> = {
  food_dining: "Food & Dining", groceries: "Groceries", electronics: "Electronics",
  shopping_fashion: "Shopping & Fashion", telecom: "Telecom", entertainment: "Entertainment",
  home_services: "Home & Services", other: "Other",
};
const CATEGORY_AR: Record<string, string> = {
  food_dining: "أكل ومطاعم", groceries: "بقالة وسوبر ماركت", electronics: "إلكترونيات وموبايلات",
  shopping_fashion: "تسوق وموضة", telecom: "اتصالات وإنترنت", entertainment: "ترفيه وخروجات",
  home_services: "البيت والخدمات", other: "أخرى",
};

export async function sendToTopic(msg: {
  topic: string;
  title: string;
  body: string;
  data?: Record<string, string>;
  imageUrl?: string | null;
}): Promise<boolean> {
  const projectId = Deno.env.get("FIREBASE_PROJECT_ID");
  const access = await googleAccessToken("https://www.googleapis.com/auth/firebase.messaging");
  if (!projectId || !access) {
    console.error("FCM not configured");
    return false;
  }
  const image = msg.imageUrl || undefined;
  const res = await fetch(`https://fcm.googleapis.com/v1/projects/${projectId}/messages:send`, {
    method: "POST",
    headers: { Authorization: `Bearer ${access}`, "Content-Type": "application/json" },
    body: JSON.stringify({
      message: {
        topic: msg.topic,
        notification: { title: msg.title, body: msg.body, ...(image && { image }) },
        data: msg.data ?? {},
        android: {
          priority: "high",
          notification: { sound: "default", channel_id: "deals_channel", ...(image && { image }) },
        },
      },
    }),
  });
  if (!res.ok) console.error(`FCM send to ${msg.topic} failed:`, res.status, await res.text());
  return res.ok;
}

function truncate(s: string, n = 100) {
  return s.length > n ? s.slice(0, n - 1) + "…" : s;
}

const isTestDeal = (title: string) => title.startsWith("[E2E]"); // automated tests never notify real users

/** Broadcast a newly approved deal to category + global topics, EN and AR. */
export async function notifyNewDeal(deal: { id: string; title: string; category: string; image_url?: string | null }) {
  if (isTestDeal(deal.title)) return;
  const emoji = CATEGORY_EMOJI[deal.category] ?? "🔥";
  const body = truncate(deal.title);
  const data = { dealId: deal.id, category: deal.category, action: "view_deal" };
  const cat = deal.category;
  await Promise.allSettled([
    sendToTopic({ topic: "all_deals", title: `${emoji} New Deal in Egypt!`, body, data, imageUrl: deal.image_url }),
    sendToTopic({ topic: `cat_${cat}`, title: `${emoji} New ${CATEGORY_EN[cat] ?? ""} Deal!`, body, data, imageUrl: deal.image_url }),
    sendToTopic({ topic: "all_deals_ar", title: `${emoji} عرض جديد في مصر!`, body, data, imageUrl: deal.image_url }),
    sendToTopic({ topic: `cat_${cat}_ar`, title: `${emoji} عرض جديد في قسم ${CATEGORY_AR[cat] ?? "أخرى"}!`, body, data, imageUrl: deal.image_url }),
  ]);
}

/** Tell the poster what happened to their deal (both language topics). */
export async function notifyDealStatus(profileId: string, deal: { id: string; title: string }, status: "approved" | "rejected", reason?: string | null) {
  if (isTestDeal(deal.title)) return;
  const data = { dealId: deal.id, action: "view_deal", type: `deal_${status}` };
  const title = truncate(deal.title, 80);
  const en = status === "approved"
    ? { t: "✅ Your deal is live!", b: title }
    : { t: "Your deal wasn't approved", b: reason ? `${title} — ${reason}` : title };
  const ar = status === "approved"
    ? { t: "✅ عرضك اتنشر!", b: title }
    : { t: "عرضك متقبلش", b: reason ? `${title} — ${reason}` : title };
  await Promise.allSettled([
    sendToTopic({ topic: `user_${profileId}`, title: en.t, body: en.b, data }),
    sendToTopic({ topic: `user_${profileId}_ar`, title: ar.t, body: ar.b, data }),
  ]);
}
