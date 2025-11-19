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

    const { deal_id, vote_type, device_id } = await req.json();

    // Validate input
    if (!deal_id || !vote_type || !device_id) {
      throw new Error("Missing required fields: deal_id, vote_type, device_id");
    }

    if (vote_type !== "hot" && vote_type !== "cold") {
      throw new Error("vote_type must be 'hot' or 'cold'");
    }

    // Check if device already voted on this deal
    const { data: existingVote, error: checkError } = await supabase
      .from("votes")
      .select("*")
      .eq("deal_id", deal_id)
      .eq("device_id", device_id)
      .single();

    if (checkError && checkError.code !== "PGRST116") {
      // PGRST116 = no rows found, which is fine
      throw checkError;
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

    // Record the vote
    const { error: insertError } = await supabase.from("votes").insert([
      {
        deal_id,
        vote_type,
        device_id,
      },
    ]);

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