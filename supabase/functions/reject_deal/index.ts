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
    const { deal_id, user_id, reason } = await req.json();
    if (!deal_id || !user_id) {
      return new Response(JSON.stringify({
        success: false,
        error: "Missing deal_id or user_id"
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
      p_permission: 'reject_deal'
    });
    if (!hasPermission) {
      return new Response(JSON.stringify({
        success: false,
        error: "Unauthorized: Only moderators/admins can reject deals"
      }), {
        status: 403,
        headers: {
          ...corsHeaders,
          "Content-Type": "application/json"
        }
      });
    }
    // Get deal info
    const { data: deal, error: fetchError } = await supabase.from("deals").select("submitted_by_user_id, title, status").eq("id", deal_id).single();
    if (fetchError || !deal) {
      return new Response(JSON.stringify({
        success: false,
        error: "Deal not found"
      }), {
        status: 404,
        headers: {
          ...corsHeaders,
          "Content-Type": "application/json"
        }
      });
    }
    if (deal.status !== 'pending') {
      return new Response(JSON.stringify({
        success: false,
        error: "Can only reject pending deals"
      }), {
        status: 400,
        headers: {
          ...corsHeaders,
          "Content-Type": "application/json"
        }
      });
    }
    // Reject deal (soft delete with rejection reason)
    const { error: updateError } = await supabase.from("deals").update({
      status: 'rejected',
      deletion_reason: reason || "Rejected by moderator"
    }).eq("id", deal_id);
    if (updateError) throw updateError;
    // Log action
    await supabase.rpc('log_action', {
      p_action_type: 'deal_rejected',
      p_deal_id: deal_id,
      p_user_id: user_id,
      p_target_user_id: deal.submitted_by_user_id,
      p_reason: reason || "No reason provided"
    });
    // ✅ FIX: Fetch the updated deal to return in response
    const { data: updatedDeal, error: fetchUpdatedError } = await supabase.from("deals").select("*").eq("id", deal_id).single();
    if (fetchUpdatedError) {
      console.error('Failed to fetch updated deal:', fetchUpdatedError);
      return new Response(JSON.stringify({
        success: true,
        message: 'Deal rejected successfully but failed to fetch updated data',
        error: fetchUpdatedError.message
      }), {
        headers: {
          ...corsHeaders,
          'Content-Type': 'application/json'
        },
        status: 200
      });
    }
    // ✅ FIX: Return the updated deal in the data field
    return new Response(JSON.stringify({
      success: true,
      message: "Deal rejected successfully",
      data: updatedDeal // ← Add this line
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
