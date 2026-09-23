// ============================================================================
// submit_feedback
// Anyone can send feedback (login optional). Rate limited per device/IP.
// The email field is optional and only used to reply.
// ============================================================================
import { admin, getCaller } from "../_shared/auth.ts";
import { ApiError, clientIp, handler, ok, readJson, str } from "../_shared/http.ts";
import { DAY, rateLimit } from "../_shared/ratelimit.ts";

Deno.serve(handler(async (req) => {
  const caller = await getCaller(req).catch(() => null);
  const body = await readJson(req);
  const deviceId = str(body.device_id ?? req.headers.get("x-device-id"), 100) ?? "unknown";
  const text = str(body.feedback_text, 2000);
  const email = str(body.email, 254);

  if (!text) throw new ApiError("VALIDATION", "Please write your feedback.", { field: "feedback_text" });
  if (text.length > 500) throw new ApiError("VALIDATION", "Feedback is too long (max 500 characters).", { field: "feedback_text" });
  if (email && !/^[^\s@]+@[^\s@]+\.[^\s@]{2,}$/.test(email)) {
    throw new ApiError("VALIDATION", "Please enter a valid email address.", { field: "email" });
  }

  await rateLimit(`feedback:dev:${deviceId}`, 5, DAY, "Thanks! You've sent a lot of feedback today. Please try again tomorrow.");
  await rateLimit(`feedback:ip:${clientIp(req)}`, 20, DAY, "Please try again tomorrow.");

  const { data, error } = await admin().from("feedback").insert({
    device_id: deviceId,
    user_id: caller?.profile.id ?? null,
    feedback_text: text,
    email: email ?? null,
    status: "pending",
  }).select("id, created_at").single();
  if (error) throw error;

  return ok({ message: "Feedback submitted", data });
}));
