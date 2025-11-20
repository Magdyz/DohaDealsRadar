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
    // ✅ UPDATED: Validate input (prioritize user_id over device_id)
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
    // ✅ UPDATED: Check for duplicate vote (prioritize user_id)
    // ========================================
    let existingVote = null;

    if (authenticatedUserId) {
      // Check by user_id (authenticated vote)
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
      // Fallback: Check by device_id (legacy support)
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

    // ========================================
    // ✅ UPDATED: Handle vote toggle logic
    // ========================================
    if (existingVote) {
      // Vote exists - either remove it or switch it
      if (existingVote.vote_type === vote_type) {
        // Same vote type - REMOVE the vote
        const { error: deleteError } = await supabase
          .from("votes")
          .delete()
          .eq("id", existingVote.id);

        if (deleteError) throw deleteError;

        // Decrement the count
        const columnToDecrement = vote_type === "hot" ? "hot_count" : "cold_count";
        const { data: deal, error: fetchError } = await supabase
          .from("deals")
          .select("hot_count, cold_count")
          .eq("id", deal_id)
          .single();

        if (fetchError) throw fetchError;

        const newCount = Math.max((deal[columnToDecrement] || 0) - 1, 0);

        const { error: updateError } = await supabase
          .from("deals")
          .update({ [columnToDecrement]: newCount })
          .eq("id", deal_id);

        if (updateError) throw updateError;

        // Get updated deal data
        const { data: updatedDeal, error: finalFetchError } = await supabase
          .from("deals")
          .select("*")
          .eq("id", deal_id)
          .single();

        if (finalFetchError) throw finalFetchError;

        return new Response(
          JSON.stringify({
            success: true,
            message: "Vote removed",
            data: updatedDeal,
          }),
          {
            headers: { ...corsHeaders, "Content-Type": "application/json" },
            status: 200,
          }
        );
      } else {
        // Different vote type - SWITCH the vote
        const { error: updateError } = await supabase
          .from("votes")
          .update({ vote_type })
          .eq("id", existingVote.id);

        if (updateError) throw updateError;

        // Adjust both counts
        const { data: deal, error: fetchError } = await supabase
          .from("deals")
          .select("hot_count, cold_count")
          .eq("id", deal_id)
          .single();

        if (fetchError) throw fetchError;

        const oldColumn = existingVote.vote_type === "hot" ? "hot_count" : "cold_count";
        const newColumn = vote_type === "hot" ? "hot_count" : "cold_count";

        const { error: countsUpdateError } = await supabase
          .from("deals")
          .update({
            [oldColumn]: Math.max((deal[oldColumn] || 0) - 1, 0),
            [newColumn]: (deal[newColumn] || 0) + 1,
          })
          .eq("id", deal_id);

        if (countsUpdateError) throw countsUpdateError;

        // Get updated deal data
        const { data: updatedDeal, error: finalFetchError } = await supabase
          .from("deals")
          .select("*")
          .eq("id", deal_id)
          .single();

        if (finalFetchError) throw finalFetchError;

        return new Response(
          JSON.stringify({
            success: true,
            message: `Vote switched to ${vote_type}`,
            data: updatedDeal,
          }),
          {
            headers: { ...corsHeaders, "Content-Type": "application/json" },
            status: 200,
          }
        );
      }
    }

    // ========================================
    // ✅ UPDATED: No existing vote - INSERT new vote
    // ========================================
    const { error: insertError } = await supabase.from("votes").insert([
      {
        deal_id,
        vote_type,
        user_id: authenticatedUserId || null,  // ✅ Store user_id
        device_id: device_id || null,           // Keep for analytics/legacy
      },
    ]);

    if (insertError) throw insertError;

    // Increment the count
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