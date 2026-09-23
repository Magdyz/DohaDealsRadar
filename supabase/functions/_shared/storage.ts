// Storage helpers
const PREFIX = "/storage/v1/object/public/deals/";

/** "https://.../storage/v1/object/public/deals/images/x.jpg" -> "images/x.jpg" (null for other buckets/hosts) */
export function storagePathFromPublicUrl(url: string | null | undefined): string | null {
  if (!url) return null;
  const base = Deno.env.get("SUPABASE_URL") + PREFIX;
  if (!url.startsWith(base)) return null;
  const path = decodeURIComponent(url.slice(base.length).split("?")[0]);
  return path.startsWith("images/") && !path.includes("..") ? path : null;
}
