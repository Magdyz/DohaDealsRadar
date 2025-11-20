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
        console.log(`⚠️ Email ${user_email} not found or not verified`);
      }
    } else if (authenticatedUserId) {
      console.log(`Using provided user_id: ${authenticatedUserId}`);
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
    // ✅ UPDATED: Record the vote with user_id
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

    const { error: insertError } = await supabase.from("votes").insert([voteData]);

    if (insertError) throw insertError;

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