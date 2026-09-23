// ============================================================================
// send-verification-code
// Sends a 6-digit email code (Supabase Auth OTP).
// Public. Rate limited per email and per IP to prevent email bombing.
// ============================================================================
import { createClient } from "npm:@supabase/supabase-js@2";
import { ApiError, clientIp, handler, ok, readJson } from "../_shared/http.ts";
import { HOUR, rateLimit } from "../_shared/ratelimit.ts";

const EMAIL_RE = /^[^\s@]+@[^\s@]+\.[^\s@]{2,}$/;

Deno.serve(handler(async (req) => {
  const body = await readJson(req);
  const email = String(body.email ?? "").trim().toLowerCase();

  if (!EMAIL_RE.test(email) || email.length > 254) {
    throw new ApiError("VALIDATION", "Please enter a valid email address.", { field: "email" });
  }

  await rateLimit(`otp:email:${email}`, 3, HOUR, "Too many codes requested for this email. Please wait before trying again.");
  await rateLimit(`otp:ip:${clientIp(req)}`, 10, HOUR, "Too many code requests. Please wait before trying again.");

  const anon = createClient(Deno.env.get("SUPABASE_URL")!, Deno.env.get("SUPABASE_ANON_KEY")!, {
    auth: { autoRefreshToken: false, persistSession: false },
  });
  const { error } = await anon.auth.signInWithOtp({ email, options: { shouldCreateUser: true } });

  if (error) {
    const code = (error as { code?: string }).code ?? "";
    console.error("signInWithOtp failed:", error.status, code, error.message);
    if (error.status === 429 || code.includes("rate_limit") || /rate limit/i.test(error.message)) {
      throw new ApiError("RATE_LIMITED", "Too many code requests right now. Please wait a few minutes and try again.", { retry_after: 300 });
    }
    if (code === "email_address_invalid" || code === "email_address_not_authorized" || code === "validation_failed") {
      throw new ApiError("VALIDATION", "We can't send a code to this email address. Please check it or use another one.", { field: "email" });
    }
    throw new ApiError("SERVER_ERROR", "We couldn't send the code. Please try again.");
  }

  return ok({ message: "Verification code sent. Check your email.", email });
}));
