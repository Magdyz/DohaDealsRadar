// ============================================================================
// Rate limiting backed by public.hit_rate_limit() (fixed windows in Postgres)
// ============================================================================
import { admin } from "./auth.ts";
import { ApiError } from "./http.ts";

export const HOUR = 3600;
export const DAY = 86400;

/**
 * Throws RATE_LIMITED (with retry_after seconds) when `key` exceeded `limit`
 * within the window. Fails open (allows) if the limiter itself errors, so a
 * limiter outage never blocks real users.
 */
export async function rateLimit(
  key: string,
  limit: number,
  windowSeconds: number,
  message: string,
  extra: Record<string, unknown> = {},
) {
  const { data, error } = await admin().rpc("hit_rate_limit", {
    p_key: key,
    p_limit: limit,
    p_window_seconds: windowSeconds,
  });
  if (error) {
    console.error("rate limiter error:", error.message);
    return;
  }
  const retryAfter = Number(data ?? 0);
  if (retryAfter > 0) throw new ApiError("RATE_LIMITED", message, { retry_after: retryAfter, ...extra });
}
