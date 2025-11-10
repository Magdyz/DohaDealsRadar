import { serve } from "https://deno.land/std@0.168.0/http/server.ts";

import { createClient } from "https://esm.sh/@supabase/supabase-js@2";



const corsHeaders = {

  "Access-Control-Allow-Origin": "*",

  "Access-Control-Allow-Headers": "authorization, x-client-info, apikey, content-type",

};



/**

 * ========================================

 * ✨ ARCHIVE DEALS CRON JOB

 * ========================================

 *

 * This function archives deals based on their expires_at date.

 *

 * NEW LOGIC: Archive deals where expires_at < NOW()

 * OLD LOGIC (deprecated): created_at < NOW() - INTERVAL '10 days'

 *

 * This function should be scheduled to run daily via Supabase Cron.

 * Schedule in Supabase Dashboard: 0 0 * * * (daily at midnight UTC)

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



    console.log("🔄 Starting archive job: Archiving expired deals...");



    // Archive all deals where expires_at is in the past and not already archived

    const { data, error } = await supabase

      .from("deals")

      .update({ is_archived: true })

      .lt("expires_at", new Date().toISOString())

      .eq("is_archived", false)

      .not("expires_at", "is", null)  // Only archive deals with a valid expiration date

      .select();



    if (error) {

      console.error("❌ Archive job failed:", error);

      throw error;

    }



    const archivedCount = data?.length || 0;

    console.log(`✅ Archive job completed: ${archivedCount} deal(s) archived`);



    return new Response(

      JSON.stringify({

        success: true,

        message: `Archive job completed: ${archivedCount} deal(s) archived`,

        archived_count: archivedCount,

        archived_deals: data

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

