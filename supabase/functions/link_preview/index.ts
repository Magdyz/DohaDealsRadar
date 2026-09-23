// ============================================================================
// link_preview
// Fetches a pasted deal link and extracts title / image / price / store so the
// post form can be pre-filled. Safe by design:
//   * https only, no IPs, no private hosts, no shorteners (validateLink)
//   * 5 s timeout, max 1.5 MB read, max 3 redirects (each re-validated)
// Best effort: many stores block bots; the app just keeps the form empty then.
// ============================================================================
import { requireUser } from "../_shared/auth.ts";
import { handler, ok, readJson, str } from "../_shared/http.ts";
import { assertPublicHost, canonicalizeUrl, hostOf, storeForHost, validateLink } from "../_shared/deals.ts";
import { HOUR, rateLimit } from "../_shared/ratelimit.ts";

const MAX_BYTES = 1_500_000;

function meta(html: string, keys: string[]): string | null {
  for (const key of keys) {
    const re = new RegExp(
      `<meta[^>]+(?:property|name|itemprop)=["']${key}["'][^>]*content=["']([^"']+)["']|<meta[^>]+content=["']([^"']+)["'][^>]*(?:property|name|itemprop)=["']${key}["']`,
      "i",
    );
    const m = html.match(re);
    if (m) return decode(m[1] ?? m[2]);
  }
  return null;
}

function decode(s: string): string {
  return s.replace(/&amp;/g, "&").replace(/&quot;/g, '"').replace(/&#39;/g, "'").replace(/&lt;/g, "<").replace(/&gt;/g, ">")
    .replace(/&#(\d+);/g, (_, d) => String.fromCharCode(Number(d))).trim();
}

function jsonLdPrice(html: string): number | null {
  const blocks = html.match(/<script[^>]+application\/ld\+json[^>]*>([\s\S]*?)<\/script>/gi) ?? [];
  for (const b of blocks) {
    const m = b.match(/"price"\s*:\s*"?([\d.,]+)"?/);
    if (m) {
      const n = Number(m[1].replace(/,/g, ""));
      if (Number.isFinite(n) && n > 0) return n;
    }
  }
  return null;
}

async function fetchLimited(start: URL): Promise<{ html: string; finalUrl: URL } | null> {
  let url = start;
  for (let hop = 0; hop < 4; hop++) {
    await assertPublicHost(url.hostname);
    const res = await fetch(url, {
      redirect: "manual",
      headers: {
        "User-Agent": "Mozilla/5.0 (Linux; Android 14) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/126 Mobile Safari/537.36 EgyptDealRadarBot/2.0",
        "Accept": "text/html,application/xhtml+xml",
        "Accept-Language": "en-EG,ar-EG;q=0.8,en;q=0.6",
      },
      signal: AbortSignal.timeout(5000),
    });
    if (res.status >= 300 && res.status < 400 && res.headers.get("location")) {
      url = validateLink(new URL(res.headers.get("location")!, url).toString());
      continue;
    }
    if (!res.ok || !(res.headers.get("content-type") ?? "").includes("html") || !res.body) return null;
    const reader = res.body.getReader();
    const chunks: Uint8Array[] = [];
    let size = 0;
    while (size < MAX_BYTES) {
      const { done, value } = await reader.read();
      if (done) break;
      chunks.push(value);
      size += value.length;
    }
    reader.cancel().catch(() => {});
    const all = new Uint8Array(size);
    let o = 0;
    for (const c of chunks) { all.set(c.subarray(0, Math.min(c.length, size - o)), o); o += c.length; if (o >= size) break; }
    return { html: new TextDecoder().decode(all), finalUrl: url };
  }
  return null;
}

Deno.serve(handler(async (req) => {
  const caller = await requireUser(req);
  await rateLimit(`preview:${caller.profile.id}`, 60, HOUR, "Too many link lookups. Please wait a bit.");
  const body = await readJson(req);
  const raw = str(body.link, 2000) ?? "";
  const url = validateLink(raw);

  const store = storeForHost(hostOf(url));
  let result: Record<string, unknown> = { title: null, image_url: null, price: null, store, canonical_url: canonicalizeUrl(url) };

  try {
    const page = await fetchLimited(url);
    if (page) {
      const { html, finalUrl } = page;
      const title = meta(html, ["og:title", "twitter:title"]) ?? (html.match(/<title[^>]*>([^<]{3,200})<\/title>/i)?.[1] ?? null);
      let image = meta(html, ["og:image", "og:image:secure_url", "twitter:image"]);
      if (image) {
        try { image = new URL(image, finalUrl).toString(); } catch { image = null; }
        if (image && !image.startsWith("https://")) image = null;
      }
      const priceRaw = meta(html, ["product:price:amount", "og:price:amount", "price"]);
      const price = priceRaw ? Number(priceRaw.replace(/[^\d.]/g, "")) || null : jsonLdPrice(html);
      result = {
        title: title ? decode(title).slice(0, 150) : null,
        image_url: image,
        price: price && price > 0 && price < 10_000_000 ? price : null,
        store: store ?? storeForHost(hostOf(finalUrl)),
        canonical_url: canonicalizeUrl(finalUrl),
      };
    }
  } catch (e) {
    console.warn("link_preview fetch failed:", (e as Error).message);
  }

  return ok({ preview: result });
}));
