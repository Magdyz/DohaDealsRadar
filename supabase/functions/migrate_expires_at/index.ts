import { serve } from "https://deno.land/std@0.168.0/http/server.ts";
import { createClient } from "https://esm.sh/@supabase/supabase-js@2";

const corsHeaders = {
  "Access-Control-Allow-Origin": "*",
  "Access-Control-Allow-Headers": "authorization, x-client-info, apikey, content-type",
};

/**
 * ========================================
 * ✨ DATA MIGRATION: Populate expires_at
 * ========================================
 *
 * This function is a ONE-TIME data migration that populates
 * the expires_at column for all existing deals.
 *
 * Logic: For all deals WHERE expires_at IS NULL,
 *        set expires_at = created_at + INTERVAL '10 days'
 *
 * This ensures all current deals conform to the new 10-day
 * expiration logic before the feature change.
 */

serve(async (req) => {

    // Handle CORS
  if (req.method === "OPTIONS") {
    return new Response("ok", { headers: corsHeaders });
  }

  try {
    const supabase = createClient(
      Deno.env.get("SUPABASE_URL") ?? "",
      Deno.env.get("SUPABASE_SERVICE_ROLE_KEY") ?? ""
    );

    console.log("🔄 Starting data migration: Populating expires_at for existing deals...");

    // Update all deals with null expires_at to have expires_at = created_at + 10 days
    const { data, error } = await supabase.rpc('migrate_expires_at_data');

    if (error) {
      console.error("❌ Migration failed:", error);
      throw error;
    }

    console.log("✅ Migration completed successfully!");

    return new Response(
      JSON.stringify({
        success: true,
        message: "Data migration completed successfully",
        data: data
      }),
      {
        headers: { ...corsHeaders, "Content-Type": "application/json" },
        status: 200,
      }
    );
  } catch (error) {
    console.error("❌ Error:", error);
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

