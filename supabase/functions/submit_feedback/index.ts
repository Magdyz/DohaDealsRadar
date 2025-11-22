import { serve } from "https://deno.land/std@0.168.0/http/server.ts";
import { createClient } from "https://esm.sh/@supabase/supabase-js@2";

/**
 * Submit user feedback
 *
 * CREATED: 2025-11-22
 *
 * Required: device_id, feedback_text
 * Optional: user_id (if authenticated)
 */

const corsHeaders = {
  "Access-Control-Allow-Origin": "*",
  "Access-Control-Allow-Headers": "authorization, x-client-info, apikey, content-type"
};

serve(async (req) => {
  // Handle CORS preflight
  if (req.method === "OPTIONS") {
    return new Response("ok", { headers: corsHeaders });
  }

  try {
    const supabase = createClient(
      Deno.env.get("SUPABASE_URL") ?? "",
      Deno.env.get("SUPABASE_SERVICE_ROLE_KEY") ?? ""
    );

    const { device_id, feedback_text, user_id, email } = await req.json();

    // Validate required fields
    if (!device_id || !feedback_text) {
      return new Response(
        JSON.stringify({
          success: false,
          error: "Missing required fields: device_id and feedback_text"
        }),
        {
          status: 400,
          headers: { ...corsHeaders, "Content-Type": "application/json" }
        }
      );
    }

    // Validate email format if provided
    if (email && email.trim() !== "") {
      const emailRegex = /^[^\s@]+@[^\s@]+\.[^\s@]+$/;
      if (!emailRegex.test(email.trim())) {
        return new Response(
          JSON.stringify({
            success: false,
            error: "Invalid email format"
          }),
          {
            status: 400,
            headers: { ...corsHeaders, "Content-Type": "application/json" }
          }
        );
      }
    }

    // Validate feedback length (max 500 characters)
    if (feedback_text.length > 500) {
      return new Response(
        JSON.stringify({
          success: false,
          error: "Feedback text exceeds maximum length of 500 characters"
        }),
        {
          status: 400,
          headers: { ...corsHeaders, "Content-Type": "application/json" }
        }
      );
    }

    // Sanitize feedback text (basic validation)
    const sanitizedText = feedback_text.trim();
    if (sanitizedText.length === 0) {
      return new Response(
        JSON.stringify({
          success: false,
          error: "Feedback text cannot be empty"
        }),
        {
          status: 400,
          headers: { ...corsHeaders, "Content-Type": "application/json" }
        }
      );
    }

    // Check if user exists (optional, just for linking)
    let validatedUserId = null;
    if (user_id) {
      const { data: user } = await supabase
        .from("users")
        .select("id")
        .eq("device_id", device_id)
        .maybeSingle();

      if (user) {
        validatedUserId = user.id;
      }
    }

    // Insert feedback into database
    const { data, error } = await supabase
      .from("feedback")
      .insert({
        device_id,
        user_id: validatedUserId,
        feedback_text: sanitizedText,
        email: email && email.trim() !== "" ? email.trim() : null,
        status: "pending"
      })
      .select()
      .single();

    if (error) {
      console.error("Error inserting feedback:", error);
      throw error;
    }

    return new Response(
      JSON.stringify({
        success: true,
        message: "Feedback submitted successfully",
        data: {
          id: data.id,
          created_at: data.created_at
        }
      }),
      {
        headers: { ...corsHeaders, "Content-Type": "application/json" },
        status: 200
      }
    );
  } catch (error) {
    console.error("Server error:", error);
    return new Response(
      JSON.stringify({
        success: false,
        error: error.message || "Server error"
      }),
      {
        headers: { ...corsHeaders, "Content-Type": "application/json" },
        status: 500
      }
    );
  }
});
