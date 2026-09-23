// ============================================================================
// delete_account
// Permanently deletes the caller's account and personal data (Google Play +
// Egypt PDPL right to erasure):
//   * votes, reports, expiry confirmations, feedback -> deleted
//   * their deals -> deleted with photos (deals are personal content)
//   * profile row + Supabase Auth user -> deleted
// Staff accounts must be demoted first (prevents accidental lock-out).
// ============================================================================
import { admin, logAction, requireUser } from "../_shared/auth.ts";
import { ApiError, handler, ok, readJson } from "../_shared/http.ts";
import { storagePathFromPublicUrl } from "../_shared/storage.ts";

Deno.serve(handler(async (req) => {
  const caller = await requireUser(req);
  const body = await readJson(req);
  if (body.confirm !== "DELETE") throw new ApiError("VALIDATION", "Please confirm account deletion.");
  if (caller.profile.role === "admin") {
    throw new ApiError("FORBIDDEN", "Admin accounts can't be deleted from the app. Ask another admin to remove your role first.");
  }

  const db = admin();
  const id = caller.profile.id;

  // Photos of their deals (and any uploads in their folder)
  const { data: deals } = await db.from("deals").select("image_url").eq("submitted_by_user_id", id);
  const paths = (deals ?? []).map((d: any) => storagePathFromPublicUrl(d.image_url)).filter(Boolean) as string[];
  const { data: folder } = await db.storage.from("deals").list(`images/${caller.authUserId}`, { limit: 1000 });
  for (const f of folder ?? []) paths.push(`images/${caller.authUserId}/${f.name}`);
  if (paths.length) {
    const { error } = await db.storage.from("deals").remove([...new Set(paths)]);
    if (error) console.error("photo cleanup failed:", error.message);
  }

  await db.from("votes").delete().eq("user_id", id);
  await db.from("reports").delete().eq("reporter_user_id", id);
  await db.from("deal_expiry_votes").delete().eq("user_id", id);
  await db.from("feedback").delete().eq("user_id", id);
  await db.from("deals").delete().eq("submitted_by_user_id", id);
  await db.from("audit_log").update({ target_user_id: null }).eq("target_user_id", id);

  const { error: delError } = await db.from("users").delete().eq("id", id);
  if (delError) {
    console.error("profile delete failed:", delError.message);
    throw new ApiError("SERVER_ERROR", "We couldn't delete your account. Please try again or contact us.");
  }
  const { error: authError } = await db.auth.admin.deleteUser(caller.authUserId);
  if (authError) console.error("auth user delete failed:", authError.message);

  await logAction("account_deleted", null, { reason: "user request" });
  return ok({ message: "Your account and data have been deleted." });
}));
