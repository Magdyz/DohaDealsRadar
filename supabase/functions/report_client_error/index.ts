// ============================================================================
// report_client_error
// The app reports that an error or crash happened, for the admin "App health"
// counts. Anonymous by design: no user id, no message text, no device ids -
// only where it happened, an error code / class name and the app version.
//   { kind: "crash"|"error", area, code?, app_version?, os_version? }
// ============================================================================
import { admin } from "../_shared/auth.ts";
import { ApiError, clientIp, handler, ok, readJson, str } from "../_shared/http.ts";
import { HOUR, rateLimit } from "../_shared/ratelimit.ts";

const clean = (v: unknown, max: number) => {
  const s = str(v, max);
  return s ? s.replace(/[^\w.\-: ]/g, "").slice(0, max) || null : null;
};

Deno.serve(handler(async (req) => {
  await rateLimit(`clienterr:${clientIp(req)}`, 30, HOUR, "Too many reports.");
  const body = await readJson(req);
  const kind = body.kind === "crash" ? "crash" : body.kind === "error" ? "error" : null;
  const area = clean(body.area, 40);
  if (!kind || !area) throw new ApiError("VALIDATION", "Missing kind or area.");

  const { error } = await admin().from("client_errors").insert({
    kind,
    area,
    code: clean(body.code, 80),
    app_version: clean(body.app_version, 20),
    os_version: clean(body.os_version, 20),
  });
  if (error) console.error("client_errors insert failed:", error.message);
  return ok({});
}));
