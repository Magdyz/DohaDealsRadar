// ============================================================================
// Google Play Integrity (standard requests) - server-side verdict decoding
//
// The app sends a token in the `x-integrity-token` header on sensitive actions
// (post, vote, report). We decode it with Google's API using the same Firebase
// service account already configured for push notifications.
//
// Mode (PLAY_INTEGRITY_MODE env):
//   "off"     - skip (default)
//   "monitor" - decode & log; risky results only hold content for review
//   "enforce" - reject requests that fail integrity
// Requires: Play Integrity API enabled in the Google Cloud project and the
// Cloud project linked in Play Console (Test and release > App integrity).
// ============================================================================
import { ApiError } from "./http.ts";
import { googleAccessToken } from "./google.ts";

const PACKAGE_NAME = "qa.deals.doha";

export type IntegrityResult = "ok" | "risky" | "unavailable";

export function integrityMode(): "off" | "monitor" | "enforce" {
  const m = (Deno.env.get("PLAY_INTEGRITY_MODE") ?? "").toLowerCase();
  return m === "enforce" || m === "monitor" ? m : "off";
}

export async function checkIntegrity(req: Request, action: string): Promise<IntegrityResult> {
  const mode = integrityMode();
  if (mode === "off") return "unavailable";

  const token = req.headers.get("x-integrity-token");
  let result: IntegrityResult = "risky";
  try {
    if (token) {
      const access = await googleAccessToken("https://www.googleapis.com/auth/playintegrity");
      if (!access) return "unavailable";
      const res = await fetch(`https://playintegrity.googleapis.com/v1/${PACKAGE_NAME}:decodeIntegrityToken`, {
        method: "POST",
        headers: { Authorization: `Bearer ${access}`, "Content-Type": "application/json" },
        body: JSON.stringify({ integrity_token: token }),
        signal: AbortSignal.timeout(4000),
      });
      const payload = (await res.json())?.tokenPayloadExternal;
      const appOk = payload?.appIntegrity?.appRecognitionVerdict === "PLAY_RECOGNIZED";
      const deviceOk = (payload?.deviceIntegrity?.deviceRecognitionVerdict ?? []).includes("MEETS_DEVICE_INTEGRITY");
      result = appOk && deviceOk ? "ok" : "risky";
      if (result === "risky") console.warn(`integrity risky for ${action}:`, JSON.stringify(payload?.deviceIntegrity ?? {}));
    }
  } catch (e) {
    console.error("integrity check failed:", e);
    return "unavailable";
  }

  if (result === "risky" && mode === "enforce") {
    throw new ApiError("FORBIDDEN", "This action isn't available on this device.", { reason: "integrity" });
  }
  return result;
}
