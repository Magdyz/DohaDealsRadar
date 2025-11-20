/**
 * ========================================
 * CAST VOTE EDGE FUNCTION
 * ========================================
 *
 * UPDATED: 2025-11-20
 * - user_id is now REQUIRED for all new votes
 * - Legacy votes with device_id only are preserved
 * - Checks both user_id and device_id for duplicate detection
 * - Comprehensive logging for debugging
 *
 * Request Body:
 * - deal_id: string (required)
 * - vote_type: "hot" | "cold" (required)
 * - user_id: string (REQUIRED - authenticated user UUID)
 * - user_email: string (optional - alternative to user_id)
 * - device_id: string (optional - kept for analytics)
 *
 * Response:
 * - success: boolean
 * - message: string
 * - data: updated deal object (if success)
 * - error: string (if failure)
 */

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
    // 📊 LOGGING: Log incoming request details
    // ========================================
    console.log(`📩 Received vote request:`);
    console.log(`   deal_id: ${deal_id}`);
    console.log(`   vote_type: ${vote_type}`);
    console.log(`   user_id: ${user_id || 'null'}`);
    console.log(`   user_email: ${user_email || 'null'}`);
    console.log(`   device_id: ${device_id || 'null'}`);

    // ========================================
    // ✅ UPDATED: Validate input - user_id is now REQUIRED
    // ========================================
    if (!deal_id || !vote_type) {
      throw new Error("Missing required fields: deal_id, vote_type");
    }

    // ✅ NEW POLICY: user_id (or user_email) is now REQUIRED for new votes
    const isAuthenticated = !!(user_id || user_email);
    if (!isAuthenticated) {
      console.log(`❌ Vote rejected: No user_id or user_email provided`);
      throw new Error("Authentication required: user_id or user_email must be provided");
    }

    if (vote_type !== "hot" && vote_type !== "cold") {
      throw new Error("vote_type must be 'hot' or 'cold'");
    }

    // ========================================
    // ✅ NEW: Lookup user_id from email if provided
    // ========================================
    let authenticatedUserId = user_id;
    if (!authenticatedUserId && user_email) {
      const { data: user, error: userError } = await supabase
        .from("users")
        .select("id")
        .eq("email", user_email)
        .eq("email_verified", true)
        .maybeSingle();

      if (user) {
        authenticatedUserId = user.id;
      } else {
        console.log(`⚠️ Email ${user_email} not found or not verified`);
      }
    }

    // ========================================
    // ✅ UPDATED: Check for duplicate vote
    // Checks both user_id (new votes) and device_id (legacy votes)
    // ========================================
    let existingVote = null;

    // PRIMARY CHECK: Check by user_id (for authenticated users)
    console.log(`🔍 Checking for existing vote by user_id: ${authenticatedUserId}`);
    const { data: userVote, error: userCheckError } = await supabase
      .from("votes")
      .select("*")
      .eq("deal_id", deal_id)
      .eq("user_id", authenticatedUserId)
      .maybeSingle();

    if (userCheckError && userCheckError.code !== "PGRST116") {
      throw userCheckError;
    }

    if (userVote) {
      console.log(`⚠️ User already voted on this deal (vote_id: ${userVote.id})`);
      existingVote = userVote;
    }

    // SECONDARY CHECK: Also check device_id for legacy votes
    // (User may have voted before authentication was required)
    if (!existingVote && device_id) {
      console.log(`🔍 Also checking for legacy vote by device_id: ${device_id}`);
      const { data: deviceVote, error: deviceCheckError } = await supabase
        .from("votes")
        .select("*")
        .eq("deal_id", deal_id)
        .eq("device_id", device_id)
        .maybeSingle();

      if (deviceCheckError && deviceCheckError.code !== "PGRST116") {
        throw deviceCheckError;
      }

      if (deviceVote) {
        console.log(`⚠️ Device already voted on this deal (legacy vote_id: ${deviceVote.id})`);
        existingVote = deviceVote;
      }
    }

    if (existingVote) {
      return new Response(
        JSON.stringify({
          success: false,
          error: "You have already voted on this deal",
        }),
        {
          headers: { ...corsHeaders, "Content-Type": "application/json" },
          status: 400,
        }
      );
    }

    // ========================================
    // ✅ UPDATED: Record the vote with user_id (REQUIRED)
    // device_id is optional (kept for analytics)
    // ========================================
    const voteRecord = {
      deal_id,
      vote_type,
      user_id: authenticatedUserId,  // ✅ REQUIRED: Always set (validated above)
      device_id: device_id || null,  // Optional: Keep for analytics
    };

    console.log(`💾 Inserting vote record into database:`);
    console.log(`   user_id: ${voteRecord.user_id}`);
    console.log(`   device_id: ${voteRecord.device_id || 'NULL'}`);
    console.log(`   vote_type: ${voteRecord.vote_type}`);

    const { data: insertedVote, error: insertError } = await supabase
      .from("votes")
      .insert([voteRecord])
      .select();

    if (insertError) throw insertError;

    console.log(`✅ Vote inserted successfully:`, insertedVote);

    // Update deal counts
    const columnToIncrement = vote_type === "hot" ? "hot_count" : "cold_count";

    const { data: deal, error: updateError } = await supabase
      .from("deals")
      .select("hot_count, cold_count")
      .eq("id", deal_id)
      .single();

    if (updateError) throw updateError;

    const newCount = (deal[columnToIncrement] || 0) + 1;

    const { error: finalUpdateError } = await supabase
      .from("deals")
      .update({ [columnToIncrement]: newCount })
      .eq("id", deal_id);

    if (finalUpdateError) throw finalUpdateError;

    // Get updated deal data
    const { data: updatedDeal, error: fetchError } = await supabase
      .from("deals")
      .select("*")
      .eq("id", deal_id)
      .single();

    if (fetchError) throw fetchError;

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