import { serve } from "https://deno.land/std@0.168.0/http/server.ts";

import { createClient } from "https://esm.sh/@supabase/supabase-js@2";



const corsHeaders = {

  "Access-Control-Allow-Origin": "*",

  "Access-Control-Allow-Headers": "authorization, x-client-info, apikey, content-type",

};



/**

 * ========================================

 * ✨ GET ARCHIVED DEALS

 * ========================================

 *

 * This function retrieves archived deals with pagination.

 *

 * Query parameters:

 * - page: Page number (default: 1)

 * - limit: Items per page (default: 20)

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



    // Parse query parameters

    const url = new URL(req.url);

    const page = parseInt(url.searchParams.get("page") || "1");

    const limit = parseInt(url.searchParams.get("limit") || "20");

    const offset = (page - 1) * limit;



    console.log(`📦 Fetching archived deals: page ${page}, limit ${limit}`);



    // Fetch archived deals with pagination

    const { data, error, count } = await supabase

      .from("deals")

      .select("*", { count: "exact" })

      .eq("is_archived", true)

      .order("created_at", { ascending: false })

      .range(offset, offset + limit - 1);



    if (error) {

      console.error("❌ Failed to fetch archived deals:", error);

      throw error;

    }



    const totalPages = Math.ceil((count || 0) / limit);



    console.log(`✅ Retrieved ${data?.length || 0} archived deals`);



    return new Response(

      JSON.stringify({

        success: true,

        data: data || [],

        pagination: {

          page,

          limit,

          total: count || 0,

          total_pages: totalPages,

          has_next: page < totalPages,

          has_previous: page > 1,

        },

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

