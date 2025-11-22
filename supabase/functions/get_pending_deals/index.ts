import { serve } from "https://deno.land/std@0.168.0/http/server.ts";
import { createClient } from "https://esm.sh/@supabase/supabase-js@2";
const corsHeaders = {
  "Access-Control-Allow-Origin": "*",
  "Access-Control-Allow-Headers": "authorization, x-client-info, apikey, content-type"
};
serve(async (req)=>{
  if (req.method === "OPTIONS") {
    return new Response("ok", {
      headers: corsHeaders
    });
  }
  try {
    const supabase = createClient(Deno.env.get("SUPABASE_URL") ?? "", Deno.env.get("SUPABASE_SERVICE_ROLE_KEY") ?? "");
    const { user_id } = await req.json();
    if (!user_id) {
      return new Response(JSON.stringify({
        success: false,
        error: "Missing user_id"
      }), {
        status: 400,
        headers: {
          ...corsHeaders,
          "Content-Type": "application/json"
        }
      });
    }
    // Check permissions
    const { data: hasPermission } = await supabase.rpc('check_permission', {
      p_user_id: user_id,
      p_permission: 'view_pending_deals'
    });
    if (!hasPermission) {
      return new Response(JSON.stringify({
        success: false,
        error: "Unauthorized: Only moderators/admins can view pending deals"
      }), {
        status: 403,
        headers: {
          ...corsHeaders,
          "Content-Type": "application/json"
        }
      });
    }
    // Get pending deals
    const { data, error } = await supabase.from("deals").select("*").eq("status", "pending").is("deleted_at", null).order("created_at", {
      ascending: false
    });
    if (error) throw error;
    return new Response(JSON.stringify({
      success: true,
      data: data || [],
      count: data?.length || 0
    }), {
      headers: {
        ...corsHeaders,
        "Content-Type": "application/json"
      },
      status: 200
    });
  } catch (error) {
    return new Response(JSON.stringify({
      success: false,
      error: error.message
    }), {
      headers: {
        ...corsHeaders,
        "Content-Type": "application/json"
      },
      status: 500
    });
  }
});
