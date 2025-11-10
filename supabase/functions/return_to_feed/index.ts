import { serve } from "https://deno.land/std@0.168.0/http/server.ts";

import { createClient } from "https://esm.sh/@supabase/supabase-js@2";



const corsHeaders = {

  "Access-Control-Allow-Origin": "*",

  "Access-Control-Allow-Headers": "authorization, x-client-info, apikey, content-type",

};



/**

 * ========================================

 * ✨ RETURN DEAL TO FEED (ADMIN ONLY)

 * ========================================

 *

 * This function allows moderators to return an archived deal

 * back to the feed with a new expiration date.

 *

 * Actions:

 * 1. Verify user is an admin

 * 2. Un-archive the deal (is_archived = false)

 * 3. Set new expires_at = NOW() + [expires_in_days] days

 * 4. Keep original created_at (for real age display)

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



    const { admin_user_id, deal_id, expires_in_days = 10 } = await req.json();



    // Validate required fields

    if (!admin_user_id || !deal_id) {

      return new Response(

        JSON.stringify({

          success: false,

          error: "Missing required fields: admin_user_id, deal_id",

        }),

        {

          status: 400,

          headers: { ...corsHeaders, "Content-Type": "application/json" },

        }

      );

    }



    console.log(`🔄 Returning deal ${deal_id} to feed by admin ${admin_user_id}`);



    // 1. Verify user is admin

    const { data: userData, error: userError } = await supabase

      .from("users")

      .select("role")

      .eq("id", admin_user_id)

      .single();



    if (userError || !userData) {

      console.error("❌ User not found:", userError);

      return new Response(

        JSON.stringify({

          success: false,

          error: "User not found",

        }),

        {

          status: 404,

          headers: { ...corsHeaders, "Content-Type": "application/json" },

        }

      );

    }



    if (userData.role !== "admin") {

      console.error("❌ User is not admin");

      return new Response(

        JSON.stringify({

          success: false,

          error: "Only admins can return deals to feed",

        }),

        {

          status: 403,

          headers: { ...corsHeaders, "Content-Type": "application/json" },

        }

      );

    }



    // 2. Calculate new expiration date

    const newExpiresAt = new Date();

    newExpiresAt.setDate(newExpiresAt.getDate() + expires_in_days);



    // 3. Update deal: un-archive and set new expiration

    const { data: dealData, error: dealError } = await supabase

      .from("deals")

      .update({

        is_archived: false,

        expires_at: newExpiresAt.toISOString(),

      })

      .eq("id", deal_id)

      .select()

      .single();



    if (dealError) {

      console.error("❌ Failed to update deal:", dealError);

      throw dealError;

    }



    console.log(`✅ Deal ${deal_id} returned to feed with new expiration: ${newExpiresAt.toISOString()}`);



    return new Response(

      JSON.stringify({

        success: true,

        message: "Deal returned to feed successfully",

        data: dealData,

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

