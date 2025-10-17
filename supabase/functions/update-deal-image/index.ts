import { serve } from "https://deno.land/std@0.168.0/http/server.ts";
import { createClient } from "https://esm.sh/@supabase/supabase-js@2";

const corsHeaders = {
"Access-Control-Allow-Origin": "*",
"Access-Control-Allow-Headers": "authorization, x-client-info, apikey, content-type",
};

serve(async (req) => {
  // Handle CORS preflight
  if (req.method === "OPTIONS") {
    return new Response("ok", { headers: corsHeaders });
  }

  try {
    // Create Supabase client with service role
    const supabase = createClient(
      Deno.env.get("SUPABASE_URL") ?? "",
      Deno.env.get("SUPABASE_SERVICE_ROLE_KEY") ?? ""
    );

    const { deal_id, image_url } = await req.json();

    console.log("🖼️  UPDATE IMAGE REQUEST:");
    console.log("  Deal ID:", deal_id);
    console.log("  New URL:", image_url);

    // Validate input
    if (!deal_id || !image_url) {
      throw new Error("Missing required fields: deal_id, image_url");
    }

    // Update the deal's image_url in the database
    const { data, error } = await supabase
      .from("deals")
      .update({ image_url: image_url })
      .eq("id", deal_id)
      .select()
      .single();

    if (error) {
      console.error("❌ Database update failed:", error);
      throw error;
    }

    console.log("✅ Image URL updated successfully");
    console.log("  Deal:", data.id);
    console.log("  New image:", data.image_url);

    return new Response(
      JSON.stringify({
        success: true,
        data: data,
      }),
      {
        status: 200,
        headers: { ...corsHeaders, "Content-Type": "application/json" },
      }
    );
  } catch (error) {
    console.error("💥 Error:", error);
    return new Response(
      JSON.stringify({
        success: false,
        error: error.message,
      }),
      {
        status: 500,
        headers: { ...corsHeaders, "Content-Type": "application/json" },
      }
    );
  }
});