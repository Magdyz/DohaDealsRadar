// ============================================================================
// create_upload_url
// Issues a short-lived signed upload URL for a deal photo, inside the
// caller's own folder: deals/images/<authUserId>/<uuid>.<ext>
// Replaces direct uploads with the public key (which let anyone upload).
// ============================================================================
import { admin, requireUser } from "../_shared/auth.ts";
import { ApiError, handler, ok, readJson } from "../_shared/http.ts";
import { HOUR, rateLimit } from "../_shared/ratelimit.ts";

Deno.serve(handler(async (req) => {
  const caller = await requireUser(req);
  const body = await readJson(req);
  const contentType = body.content_type === "image/webp" ? "image/webp" : "image/jpeg";
  const ext = contentType === "image/webp" ? "webp" : "jpg";

  await rateLimit(`upload:${caller.profile.id}`, 30, HOUR, "Too many uploads. Please wait a bit and try again.");

  const path = `images/${caller.authUserId}/${crypto.randomUUID()}.${ext}`;
  const { data, error } = await admin().storage.from("deals").createSignedUploadUrl(path);
  if (error || !data) {
    console.error("createSignedUploadUrl failed:", error?.message);
    throw new ApiError("SERVER_ERROR", "We couldn't prepare the upload. Please try again.");
  }

  const base = Deno.env.get("SUPABASE_URL");
  return ok({
    path,
    upload_url: data.signedUrl,          // PUT the file here (Content-Type: image/jpeg|webp)
    token: data.token,
    content_type: contentType,
    public_url: `${base}/storage/v1/object/public/deals/${path}`,
  });
}));
