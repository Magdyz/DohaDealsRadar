import { serve } from "https://deno.land/std@0.177.0/http/server.ts";
import { createClient } from "https://esm.sh/@supabase/supabase-js@2";
const supabaseUrl = Deno.env.get("SUPABASE_URL");
const supabaseKey = Deno.env.get("SUPABASE_SERVICE_ROLE_KEY");
const supabase = createClient(supabaseUrl, supabaseKey);
serve(async (req)=>{
  try {
    const body = await req.json();
    console.log("📩 Incoming vote:", body);
    const { deal_id, vote, device_id } = body;
    // 1. Validate input
    if (!deal_id || !vote || !device_id) {
      return new Response("Missing fields: deal_id, vote, device_id required", {
        status: 400
      });
    }
    if (vote !== "hot" && vote !== "cold") {
      return new Response("Invalid vote value. Must be 'hot' or 'cold'", {
        status: 400
      });
    }
    // 2. Check if this device already voted for this deal
    const { data: existingVote, error: checkError } = await supabase.from("votes").select("*").eq("deal_id", deal_id).eq("device_id", device_id).maybeSingle();
    if (checkError) {
      console.error("❌ Error checking existing vote:", checkError);
      throw checkError;
    }
    if (existingVote) {
      return new Response("❗ You have already voted for this deal", {
        status: 409
      });
    }
    // 3. Insert the vote
    const { error: insertError } = await supabase.from("votes").insert([
      {
        deal_id,
        device_id,
        vote
      }
    ]);
    if (insertError) {
      console.error("❌ Failed to insert vote:", insertError);
      throw insertError;
    }
    // 4. Update vote counters on the deal
    const column = vote === "hot" ? "hot_count" : "cold_count";
    const { error: updateError } = await supabase.rpc("increment_vote_count", {
      deal_id_param: deal_id,
      column_name: column
    });
    if (updateError) {
      console.error("❌ Failed to update counts:", updateError);
      throw updateError;
    }
    return new Response(JSON.stringify({
      message: "✅ Vote recorded",
      deal_id,
      vote,
      device_id
    }), {
      status: 200
    });
  } catch (e) {
    console.error("🔥 Vote failed:", e);
    return new Response("Server error: " + e.message, {
      status: 500
    });
  }
});
