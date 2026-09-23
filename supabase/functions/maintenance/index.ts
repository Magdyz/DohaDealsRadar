// ============================================================================
// maintenance - daily job (pg_cron -> pg_net), authenticated by CRON_SECRET
//   1. archive expired deals
//   2. purge data past its retention period (purge_old_data)
//   3. delete the purged deals' photos from Storage
// ============================================================================
import { admin } from "../_shared/auth.ts";
import { ApiError, handler, ok } from "../_shared/http.ts";
import { storagePathFromPublicUrl } from "../_shared/storage.ts";

function timingSafeEqual(a: string, b: string) {
  if (a.length !== b.length) return false;
  let r = 0;
  for (let i = 0; i < a.length; i++) r |= a.charCodeAt(i) ^ b.charCodeAt(i);
  return r === 0;
}

Deno.serve(handler(async (req) => {
  const secret = Deno.env.get("CRON_SECRET") ?? "";
  const given = req.headers.get("x-cron-secret") ?? "";
  if (!secret || !timingSafeEqual(secret, given)) throw new ApiError("FORBIDDEN", "Internal endpoint");

  const db = admin();
  const { data: archived, error: archiveError } = await db.rpc("archive_expired_deals");
  if (archiveError) console.error("archive failed:", archiveError.message);

  const { data: purged, error: purgeError } = await db.rpc("purge_old_data");
  if (purgeError) throw purgeError;
  const row = (purged as any[])?.[0] ?? {};

  const paths = ((row.image_paths ?? []) as string[]).map(storagePathFromPublicUrl).filter(Boolean) as string[];
  let removed = 0;
  for (let i = 0; i < paths.length; i += 100) {
    const { data, error } = await db.storage.from("deals").remove(paths.slice(i, i + 100));
    if (error) console.error("photo purge failed:", error.message);
    removed += data?.length ?? 0;
  }

  const result = {
    archived: (archived as any[])?.[0]?.archived_count ?? 0,
    deals_deleted: row.deals_deleted ?? 0,
    reports_deleted: row.reports_deleted ?? 0,
    feedback_deleted: row.feedback_deleted ?? 0,
    photos_deleted: removed,
  };
  console.log("maintenance:", JSON.stringify(result));
  return ok(result);
}));
