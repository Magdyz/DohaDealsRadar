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
    // LOOKUP USER_ID FROM EMAIL IF PROVIDED
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
    // ✅ NEW: CALL ATOMIC TRANSACTION FUNCTION
    // This replaces the old duplicate check + manual insert + count update
    // The transaction function handles:
    // - NEW vote: Insert + increment count
    // - SWITCH vote: Update vote_type + adjust counts
    // - REMOVE vote: Delete + decrement count
    // All in a single atomic transaction with row-level locks
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

  } catch (error) {
    console.error("🔥 Cast vote failed:", error);
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

// ========================================
// OLD IMPLEMENTATION (KEPT FOR REFERENCE/ROLLBACK)
// ========================================
// The code below is the previous implementation that blocked vote switching.
// It's kept here for easy rollback if issues are discovered.
// To rollback: Replace the new implementation above with this code.
/*
    // OLD: Check for duplicate vote (blocked all repeats)
    let existingVote = null;
    if (authenticatedUserId) {
      const { data, error: checkError } = await supabase
        .from("votes")
        .select("*")
        .eq("deal_id", deal_id)
        .eq("user_id", authenticatedUserId)
        .maybeSingle();
      if (checkError && checkError.code !== "PGRST116") {
        throw checkError;
      }
      existingVote = data;
    } else if (device_id) {
      const { data, error: checkError } = await supabase
        .from("votes")
        .select("*")
        .eq("deal_id", deal_id)
        .eq("device_id", device_id)
        .maybeSingle();
      if (checkError && checkError.code !== "PGRST116") {
        throw checkError;
      }
      existingVote = data;
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

    // OLD: Manual insert and count update (non-atomic)
    const { error: insertError } = await supabase.from("votes").insert([
      {
        deal_id,
        vote_type,
        user_id: authenticatedUserId || null,
        device_id: device_id || null,
      },
    ]);
    if (insertError) throw insertError;

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