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
    // LOOKUP USER_ID FROM EMAIL IF PROVIDED
    // ========================================
    let authenticatedUserId = user_id;

    // If user_id not provided but email is, look up user by email
    if (!authenticatedUserId && user_email) {
      console.log(`Looking up user by email: ${user_email}`);
      const { data: user, error: userError } = await supabase
        .from("users")
        .select("id")
        .eq("email", user_email)
        .eq("email_verified", true)
        .maybeSingle();

      if (userError) {
        console.error(`Error looking up user by email:`, userError);
      }

      if (user) {
        authenticatedUserId = user.id;
        console.log(`Found user_id from email: ${authenticatedUserId}`);
      } else {
        console.log(`❌ Email ${user_email} not found or not verified`);
        throw new Error(`User with email ${user_email} not found or not verified`);
      }
    } else if (authenticatedUserId) {
      console.log(`Using provided user_id: ${authenticatedUserId}`);
    }

    // ========================================
    // ✅ VALIDATION: Ensure we have a valid user_id
    // ========================================
    if (!authenticatedUserId) {
      console.log(`❌ No valid user_id found after authentication checks`);
      throw new Error("Failed to authenticate user: no valid user_id");
    }

    console.log(`✅ Authenticated user_id: ${authenticatedUserId}`);

    // ========================================
    // ✅ VALIDATION: Verify user exists in database
    // ========================================
    const { data: userExists, error: userExistsError } = await supabase
      .from("users")
      .select("id")
      .eq("id", authenticatedUserId)
      .maybeSingle();

    if (userExistsError) {
      console.log(`❌ Error checking if user exists: ${userExistsError.message}`);
      throw new Error(`Failed to verify user: ${userExistsError.message}`);
    }

    if (!userExists) {
      console.log(`❌ User ${authenticatedUserId} does not exist in database`);
      throw new Error(`User ${authenticatedUserId} not found`);
    }

    console.log(`✅ User verified in database`);

    // ========================================
    // ✅ UPDATED: Check for duplicate vote
    // Checks both user_id (new votes) and device_id (legacy votes)
    // ========================================
    const { data: voteResult, error: voteError } = await supabase
      .rpc('cast_vote_atomic', {
        p_deal_id: deal_id,
        p_vote_type: vote_type,
        p_user_id: authenticatedUserId || null,
        p_device_id: device_id || null
      });

    if (voteError) {
      console.error("❌ Vote transaction failed:", voteError);
      throw voteError;
    }

    // voteResult is array with single row: [{ action, hot_count, cold_count }]
    const result = voteResult && voteResult.length > 0 ? voteResult[0] : null;

    if (!result) {
      throw new Error("Vote transaction returned no result");
    }

    console.log(`✅ Vote ${result.action}: user=${authenticatedUserId || device_id}, deal=${deal_id}, type=${vote_type}`);

    // ========================================
    // FETCH UPDATED DEAL DATA
    // ========================================
    const { data: updatedDeal, error: fetchError } = await supabase
      .from("deals")
      .select("*")
      .eq("id", deal_id)
      .single();

    if (fetchError) throw fetchError;

    // ========================================
    // RETURN SUCCESS RESPONSE
    // ========================================
    const messages = {
      'added': `Vote recorded: ${vote_type}`,
      'switched': `Vote changed to: ${vote_type}`,
      'removed': `Vote removed`
    };

    return new Response(
      JSON.stringify({
        success: true,
        message: messages[result.action] || `Vote ${result.action}`,
        action: result.action,  // ✅ NEW: Tell client what happened
        data: updatedDeal,
      }),
      {
        headers: { ...corsHeaders, "Content-Type": "application/json" },
        status: 200,
      }
    );

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
    // Log the vote attempt for debugging
    console.log(`Received vote request: deal=${deal_id}, type=${vote_type}`);
    console.log(`  user_id=${user_id}, user_email=${user_email}, device_id=${device_id}`);

    // Prepare vote data: prioritize user_id, keep device_id for analytics only if user_id present
    const voteData: any = {
      deal_id,
      vote_type,
      user_id: authenticatedUserId || null,
      // Store device_id for analytics (it's now allowed alongside user_id)
      device_id: device_id || null,
    };

    console.log(`Inserting vote with user_id=${voteData.user_id}, device_id=${voteData.device_id}`);

    const { data: insertedVote, error: insertError } = await supabase.from("votes").insert([voteData]).select();

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
*/
