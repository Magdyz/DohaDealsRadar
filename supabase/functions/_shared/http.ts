// ============================================================================
// Shared HTTP helpers for all Edge Functions
//
// Every error response has the same shape so the app can show a clear,
// translated message:  { success: false, code: "ERROR_CODE", error: "English
// fallback text", retry_after?: seconds, ...extra }
// The app maps `code` to EN/AR strings; `error` is only a fallback/log text.
// ============================================================================

export const corsHeaders = {
  "Access-Control-Allow-Origin": "*",
  "Access-Control-Allow-Headers": "authorization, x-client-info, apikey, content-type, x-device-id, x-integrity-token",
  "Access-Control-Allow-Methods": "GET, POST, OPTIONS",
};

export type ErrorCode =
  | "UNAUTHORIZED"          // no / invalid / expired session -> log in again
  | "FORBIDDEN"             // logged in but not allowed
  | "BANNED"                // account suspended
  | "NOT_FOUND"
  | "VALIDATION"            // bad input; `field` says which
  | "RATE_LIMITED"          // `retry_after` seconds
  | "DUPLICATE_DEAL"        // same deal already active; `existing` has it
  | "POSSIBLE_DUPLICATE"    // similar deals found; client may confirm
  | "LINK_BLOCKED"          // unsafe / shortener / not https
  | "OWN_DEAL"              // e.g. voting on your own deal
  | "ALREADY_DONE"          // e.g. already reported
  | "DEAL_UNAVAILABLE"      // expired / removed / not approved
  | "INVALID_CODE"          // wrong or expired verification code
  | "SERVER_ERROR";

export function json(body: unknown, status = 200, extraHeaders: Record<string, string> = {}): Response {
  return new Response(JSON.stringify(body), {
    status,
    headers: { ...corsHeaders, "Content-Type": "application/json", ...extraHeaders },
  });
}

export function ok(body: Record<string, unknown> = {}, status = 200): Response {
  return json({ success: true, ...body }, status);
}

const STATUS: Record<ErrorCode, number> = {
  UNAUTHORIZED: 401,
  FORBIDDEN: 403,
  BANNED: 403,
  NOT_FOUND: 404,
  VALIDATION: 400,
  RATE_LIMITED: 429,
  DUPLICATE_DEAL: 409,
  POSSIBLE_DUPLICATE: 409,
  LINK_BLOCKED: 400,
  OWN_DEAL: 400,
  ALREADY_DONE: 409,
  DEAL_UNAVAILABLE: 410,
  INVALID_CODE: 400,
  SERVER_ERROR: 500,
};

export class ApiError extends Error {
  constructor(
    public code: ErrorCode,
    message: string,
    public extra: Record<string, unknown> = {},
  ) {
    super(message);
  }
}

export function fail(code: ErrorCode, message: string, extra: Record<string, unknown> = {}): Response {
  return json({ success: false, code, error: message, ...extra }, STATUS[code]);
}

/**
 * Wraps a handler: CORS preflight, JSON errors, no stack traces or internal
 * details leaked to clients (they go to the function logs only).
 */
export function handler(fn: (req: Request) => Promise<Response>) {
  return async (req: Request): Promise<Response> => {
    if (req.method === "OPTIONS") return new Response("ok", { headers: corsHeaders });
    try {
      return await fn(req);
    } catch (e) {
      if (e instanceof ApiError) return fail(e.code, e.message, e.extra);
      console.error("Unhandled error:", e);
      return fail("SERVER_ERROR", "Something went wrong. Please try again.");
    }
  };
}

/** Parses a JSON body; returns {} for empty bodies, 400 for malformed JSON. */
export async function readJson(req: Request): Promise<Record<string, any>> {
  if (req.method === "GET") return {};
  const text = await req.text();
  if (!text.trim()) return {};
  try {
    const parsed = JSON.parse(text);
    return parsed && typeof parsed === "object" ? parsed : {};
  } catch {
    throw new ApiError("VALIDATION", "Malformed JSON body");
  }
}

export function clientIp(req: Request): string {
  return (req.headers.get("x-forwarded-for") ?? "").split(",")[0].trim() || "unknown";
}

export function str(v: unknown, max = 10_000): string | null {
  if (typeof v !== "string") return null;
  const t = v.trim();
  return t.length ? t.slice(0, max) : null;
}

export function isUuid(v: unknown): v is string {
  return typeof v === "string" && /^[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}$/i.test(v);
}

/**
 * Finish work after the response is sent (e.g. push notifications), so it can
 * never slow down or fail the user's request. Errors are logged, not thrown.
 */
export function background(task: Promise<unknown>) {
  const safe = task.catch((e) => console.error("background task failed:", e));
  // deno-lint-ignore no-explicit-any
  const runtime = (globalThis as any).EdgeRuntime;
  if (runtime?.waitUntil) runtime.waitUntil(safe);
}
