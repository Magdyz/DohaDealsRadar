import { serve } from "https://deno.land/std@0.168.0/http/server.ts";
import { createClient } from "https://esm.sh/@supabase/supabase-js@2";

const corsHeaders = {
  "Access-Control-Allow-Origin": "*",
  "Access-Control-Allow-Headers": "authorization, x-client-info, apikey, content-type",
};

serve(async (req) => {
  if (req.method === "OPTIONS") {
    return new Response("ok", { headers: corsHeaders });
  }

  try {
    const supabase = createClient(
      Deno.env.get("SUPABASE_URL") ?? "",
      Deno.env.get("SUPABASE_SERVICE_ROLE_KEY") ?? ""
    );

    const { deal_id, vote_type, device_id, user_id, user_email } = await req.json();

    // ========================================
    // VALIDATION
    // ========================================
    if (!deal_id || !vote_type) {
      throw new Error("Missing required fields: deal_id, vote_type");
    }

    const isAuthenticated = !!(user_id || user_email);
    if (!isAuthenticated && !device_id) {
      throw new Error("Either user_id/user_email or device_id is required");
    }

    if (vote_type !== "hot" && vote_type !== "cold") {
      throw new Error("vote_type must be 'hot' or 'cold'");
    }

    // ========================================
    // USER LOOKUP (if email provided)
    // ========================================
    let finalUserId = user_id;
    if (!finalUserId && user_email) {
      const { data: user, error: userError } = await supabase
        .from("users")
        .select("id")
        .eq("email", user_email)
        .eq("email_verified", true)
        .maybeSingle();

      if (user) {
        finalUserId = user.id;
      } else {
        console.log(`⚠️ Email ${user_email} not found or not verified`);
      }
    }

    // ========================================
    // ATOMIC UPSERT (The Final Fix)
    // Call the Postgres RPC function directly.
    // This bypasses the JS client's limitation with Partial Indexes.
    // ========================================

    const { error: voteError } = await supabase.rpc('cast_vote', {
      p_deal_id: deal_id,
      p_vote_type: vote_type,
      p_user_id: finalUserId || null,
      p_device_id: device_id || null
    });

    if (voteError) {
      console.error("Vote Error:", voteError);
      throw voteError;
    }

    // ========================================
    // UPDATE DEAL COUNTS
    // Recalculate hot_count and cold_count from votes table
    // ========================================
    const { count: hotCount } = await supabase
      .from("votes")
      .select("*", { count: "exact", head: true })
      .eq("deal_id", deal_id)
      .eq("vote_type", "hot");

    const { count: coldCount } = await supabase
      .from("votes")
      .select("*", { count: "exact", head: true })
      .eq("deal_id", deal_id)
      .eq("vote_type", "cold");

    const { error: updateError } = await supabase
      .from("deals")
      .update({
        hot_count: hotCount || 0,
        cold_count: coldCount || 0,
      })
      .eq("id", deal_id);

    if (updateError) {
      console.error("Deal count update error:", updateError);
      throw updateError;
    }

    console.log(
      `✅ Vote recorded for deal ${deal_id}: ${vote_type} ` +
      `(hot=${hotCount || 0}, cold=${coldCount || 0})`
    );

    // ========================================
    // FETCH UPDATED DEAL (for backward compatibility with client)
    // ========================================
    const { data: updatedDeal, error: dealError } = await supabase
      .from("deals")
      .select("*")
      .eq("id", deal_id)
      .single();

    if (dealError) throw dealError;

    return new Response(
      JSON.stringify({
        success: true,
        message: `Vote recorded: ${vote_type}`,
        data: updatedDeal,
      }),
      {
        headers: { ...corsHeaders, "Content-Type": "application/json" },
        status: 200,
      }
    );
  } catch (error) {
    return new Response(
      JSON.stringify({
        success: false,
        error: error.message,
      }),
      {
        headers: { ...corsHeaders, "Content-Type": "application/json" },
        status: 500,
      }
    );
  }
});
