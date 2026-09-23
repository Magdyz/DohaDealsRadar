// ============================================================================
// Deal helpers: public columns, validation, URL canonicalization, link safety
// ============================================================================
import { ApiError } from "./http.ts";

/** Columns safe to return to any client (no device ids, no internal user ids). */
export const PUBLIC_DEAL_COLUMNS = [
  "id", "title", "description", "image_url", "link", "posted_by", "created_at", "expires_at",
  "status", "hot_count", "cold_count", "location", "category", "promo_code", "is_archived",
  "original_price", "discounted_price", "governorate", "store", "report_count", "expired_votes",
  "auto_approved", "deleted_at", "deletion_reason",
].join(", ");

/** Staff also see who submitted/approved (still no device ids). */
export const STAFF_DEAL_COLUMNS = PUBLIC_DEAL_COLUMNS + ", submitted_by_user_id, approved_by, approved_at, requires_review";

export const CATEGORIES = [
  "food_dining", "groceries", "electronics", "shopping_fashion",
  "telecom", "entertainment", "home_services", "other",
];

/** Egypt's 27 governorates + online/nationwide. Keep in sync with the app. */
export const GOVERNORATES = [
  "all_egypt", "cairo", "giza", "alexandria", "qalyubia", "sharqia", "dakahlia", "gharbia",
  "monufia", "beheira", "kafr_el_sheikh", "damietta", "port_said", "ismailia", "suez",
  "fayoum", "beni_suef", "minya", "asyut", "sohag", "qena", "luxor", "aswan",
  "red_sea", "new_valley", "matrouh", "north_sinai", "south_sinai",
];

/** Well-known Egyptian / regional retailers: posts linking here are lower risk. */
export const TRUSTED_STORE_HOSTS: Record<string, string> = {
  "amazon.eg": "Amazon.eg",
  "noon.com": "noon",
  "jumia.com.eg": "Jumia",
  "btech.com": "B.TECH",
  "2b.com.eg": "2B",
  "carrefouregypt.com": "Carrefour",
  "raya.com": "Raya",
  "elarabygroup.com": "El Araby",
  "talabat.com": "talabat",
  "breadfast.com": "Breadfast",
  "rabbitmart.com": "Rabbit",
  "instashop.com": "InstaShop",
  "ikea.com": "IKEA",
  "hm.com": "H&M",
  "zara.com": "Zara",
  "defacto.com": "DeFacto",
  "lcwaikiki.eg": "LC Waikiki",
  "shein.com": "SHEIN",
  "vodafone.com.eg": "Vodafone",
  "orange.eg": "Orange",
  "etisalat.eg": "e&",
  "te.eg": "WE",
  "valu.com.eg": "valU",
  "sympl.ai": "Sympl",
  "kazyon.com": "Kazyon",
  "spinneys-egypt.com": "Spinneys",
  "hyperone.com.eg": "HyperOne",
  "metro-markets.com": "Metro",
};

const SHORTENERS = [
  "bit.ly", "tinyurl.com", "t.co", "goo.gl", "ow.ly", "is.gd", "buff.ly", "cutt.ly", "rb.gy",
  "shorturl.at", "rebrand.ly", "tiny.cc", "s.id", "v.gd", "lnkd.in", "t.ly", "shorte.st",
];

const TRACKING_PARAMS = /^(utm_.*|fbclid|gclid|dclid|msclkid|igshid|mc_(cid|eid)|ref|ref_|tag|linkCode|linkId|camp|creative|creativeASIN|ascsubtag|smid|psc|th|_encoding|pd_rd_.*|pf_rd_.*|sprefix|crid|qid|sr|keywords|spm|scm|aff.*|affiliate.*|clickid|irclickid|srsltid)$/i;

export function hostOf(url: URL): string {
  return url.hostname.toLowerCase().replace(/^(www\.|m\.|egypt\.)/, "");
}

export function storeForHost(host: string): string | null {
  for (const [h, name] of Object.entries(TRUSTED_STORE_HOSTS)) {
    if (host === h || host.endsWith("." + h)) return name;
  }
  return null;
}

/**
 * Validates a user-provided deal link. Returns the parsed URL.
 * Throws LINK_BLOCKED for non-https, IP hosts, shorteners, credentials in URL.
 */
export function validateLink(raw: string): URL {
  let url: URL;
  try {
    url = new URL(raw.trim());
  } catch {
    throw new ApiError("VALIDATION", "Please enter a valid link.", { field: "link" });
  }
  if (url.protocol !== "https:") throw new ApiError("LINK_BLOCKED", "Only secure (https) links are allowed.", { reason: "not_https" });
  if (url.username || url.password) throw new ApiError("LINK_BLOCKED", "This link can't be posted.", { reason: "credentials" });
  const host = hostOf(url);
  if (/^\d+\.\d+\.\d+\.\d+$/.test(host) || host.includes(":") || !host.includes(".") ||
      /(^|\.)(localhost|local|internal|intranet|lan|home|corp|supabase\.co|supabase\.in)$/.test(host) ||
      (url.port && url.port !== "443")) {
    throw new ApiError("LINK_BLOCKED", "This link can't be posted.", { reason: "ip_host" });
  }
  if (SHORTENERS.some((s) => host === s || host.endsWith("." + s))) {
    throw new ApiError("LINK_BLOCKED", "Please paste the full store link, not a shortened one.", { reason: "shortener" });
  }
  if (raw.length > 2000) throw new ApiError("VALIDATION", "Link is too long.", { field: "link" });
  return url;
}

function isPrivateIp(ip: string): boolean {
  if (ip.includes(":")) {
    const v = ip.toLowerCase();
    return v === "::1" || v.startsWith("fc") || v.startsWith("fd") || v.startsWith("fe80") || v === "::";
  }
  const [a, b] = ip.split(".").map(Number);
  return a === 10 || a === 127 || a === 0 || (a === 169 && b === 254) || (a === 172 && b >= 16 && b <= 31) ||
    (a === 192 && b === 168) || (a === 100 && b >= 64 && b <= 127) || a >= 224;
}

/** SSRF guard for server-side fetches: the host must resolve to public IPs only. */
export async function assertPublicHost(host: string) {
  try {
    const records = [
      ...(await Deno.resolveDns(host, "A").catch(() => [] as string[])),
      ...(await Deno.resolveDns(host, "AAAA").catch(() => [] as string[])),
    ];
    if (!records.length || records.some(isPrivateIp)) {
      throw new ApiError("LINK_BLOCKED", "This link can't be opened.", { reason: "private_host" });
    }
  } catch (e) {
    if (e instanceof ApiError) throw e;
    // resolveDns not available in this runtime: rely on hostname checks
  }
}

/**
 * Canonical form used for duplicate detection: lowercase host without www,
 * no tracking params / fragments, trailing slash removed, and product ids
 * extracted for big stores (e.g. Amazon ASIN) so different URL shapes match.
 */
export function canonicalizeUrl(url: URL): string {
  const host = hostOf(url);
  const path = url.pathname.replace(/\/+$/, "");

  // Amazon: /dp/ASIN or /gp/product/ASIN
  if (host.startsWith("amazon.")) {
    const m = path.match(/\/(?:dp|gp\/product|gp\/aw\/d)\/([A-Z0-9]{10})/i);
    if (m) return `${host}/dp/${m[1].toUpperCase()}`;
  }
  // noon: .../<SKU>/p/
  if (host === "noon.com") {
    const m = path.match(/\/([A-Z0-9]{10,})\/p$/i) ?? path.match(/\/([A-Z0-9]{10,})\/p\//i);
    if (m) return `noon.com/p/${m[1].toUpperCase()}`;
  }

  const params = [...url.searchParams.entries()]
    .filter(([k]) => !TRACKING_PARAMS.test(k))
    .sort(([a], [b]) => a.localeCompare(b));
  const qs = params.length ? "?" + new URLSearchParams(params).toString() : "";
  return `${host}${path.toLowerCase()}${qs}`;
}

/** Lowercase, strip punctuation/emoji/diacritics, normalize Arabic letters & digits. */
export function normalizeTitle(title: string): string {
  return title
    .toLowerCase()
    .replace(/[٠-٩]/g, (d) => String(d.charCodeAt(0) - 0x0660))
    .replace(/[۰-۹]/g, (d) => String(d.charCodeAt(0) - 0x06F0))
    .replace(/[ً-ٰٟ]/g, "")       // Arabic diacritics
    .replace(/[إأآا]/g, "ا").replace(/ى/g, "ي").replace(/ة/g, "ه")
    .replace(/[^\p{L}\p{N}\s]/gu, " ")
    .replace(/\s+/g, " ")
    .trim();
}

/** Optional Google Safe Browsing check (only when SAFE_BROWSING_API_KEY is set). */
export async function isUnsafeUrl(url: string): Promise<boolean> {
  const key = Deno.env.get("SAFE_BROWSING_API_KEY");
  if (!key) return false;
  try {
    const res = await fetch(`https://safebrowsing.googleapis.com/v4/threatMatches:find?key=${key}`, {
      method: "POST",
      headers: { "Content-Type": "application/json" },
      body: JSON.stringify({
        client: { clientId: "egyptdealradar", clientVersion: "2.0" },
        threatInfo: {
          threatTypes: ["MALWARE", "SOCIAL_ENGINEERING", "UNWANTED_SOFTWARE", "POTENTIALLY_HARMFUL_APPLICATION"],
          platformTypes: ["ANY_PLATFORM"],
          threatEntryTypes: ["URL"],
          threatEntries: [{ url }],
        },
      }),
      signal: AbortSignal.timeout(3000),
    });
    const body = await res.json();
    return Array.isArray(body?.matches) && body.matches.length > 0;
  } catch (e) {
    console.error("Safe Browsing check failed (allowing):", e);
    return false;
  }
}

/** Image URLs must point to OUR bucket, inside the caller's own folder. */
export function assertOwnImageUrl(imageUrl: string, authUserId: string) {
  const base = `${Deno.env.get("SUPABASE_URL")}/storage/v1/object/public/deals/images/${authUserId}/`;
  if (!imageUrl.startsWith(base) || imageUrl.includes("..")) {
    throw new ApiError("VALIDATION", "Please upload the photo again.", { field: "image" });
  }
}

export function parsePrice(v: unknown, field: string): number | null {
  if (v === null || v === undefined || v === "") return null;
  const n = Number(v);
  if (!Number.isFinite(n) || n <= 0 || n > 10_000_000) {
    throw new ApiError("VALIDATION", "Please enter a valid price.", { field });
  }
  return Math.round(n * 100) / 100;
}
