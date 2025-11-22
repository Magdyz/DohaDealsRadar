import { serve } from "https://deno.land/std@0.168.0/http/server.ts";
import { createClient } from "https://esm.sh/@supabase/supabase-js@2";

/**
 * Resolve a report with action
 * Takes specific action on the reported content
 *
 * CREATED: 2025-11-22
 *
 * Required: report_id, user_id (moderator/admin), action
 * Optional: reason
 *
 * Actions:
 * - delete_deal: Soft delete the reported deal
 * - warn_user: Create a warning for the user (future implementation)
 * - ban_user: Ban the user who posted (future implementation)
 */

const corsHeaders = {
  "Access-Control-Allow-Origin": "*",
  "Access-Control-Allow-Headers": "authorization, x-client-info, apikey, content-type"
};

serve(async (req) => {
  // Handle CORS preflight
  if (req.method === "OPTIONS") {
    return new Response("ok", { headers: corsHeaders });
  }

  try {
    const supabase = createClient(
      Deno.env.get("SUPABASE_URL") ?? "",
      Deno.env.get("SUPABASE_SERVICE_ROLE_KEY") ?? ""
    );

    const { report_id, user_id, action, reason } = await req.json();

    if (!report_id || !user_id || !action) {
      return new Response(
        JSON.stringify({
          success: false,
          error: "Missing required fields: report_id, user_id, action"
        }),
        {
          status: 400,
          headers: { ...corsHeaders, "Content-Type": "application/json" }
        }
      );
    }

    // Check permissions (moderator or admin only)
    const { data: hasPermission } = await supabase.rpc('check_permission', {
      p_user_id: user_id,
      p_permission: 'manage_reports'
    });

    if (!hasPermission) {
      return new Response(
        JSON.stringify({
          success: false,
          error: "Unauthorized: Only moderators/admins can resolve reports"
        }),
        {
          status: 403,
          headers: { ...corsHeaders, "Content-Type": "application/json" }
        }
      );
    }

    // Fetch the report to get the deal_id
    const { data: report, error: fetchError } = await supabase
      .from("reports")
      .select("id, deal_id")
      .eq("id", report_id)
      .single();

    if (fetchError || !report) {
      return new Response(
        JSON.stringify({
          success: false,
          error: "Report not found"
        }),
        {
          status: 404,
          headers: { ...corsHeaders, "Content-Type": "application/json" }
        }
      );
    }

    // Perform the action
    let actionMessage = "";

    switch (action) {
      case "delete_deal":
        // Soft delete the deal
        const { error: deleteError } = await supabase
          .from("deals")
          .update({
            deleted_at: new Date().toISOString(),
            deleted_by: user_id,
            deletion_reason: reason || "Reported by user"
          })
          .eq("id", report.deal_id);

        if (deleteError) {
          console.error("Error deleting deal:", deleteError);
          throw deleteError;
        }
        actionMessage = "Deal deleted successfully";
        break;

      case "warn_user":
        // Future implementation: Create warning record
        // For now, just acknowledge the action
        actionMessage = "User warning created (future implementation)";
        break;

      case "ban_user":
        // Future implementation: Ban the user
        // For now, just acknowledge the action
        actionMessage = "User ban created (future implementation)";
        break;

      default:
        return new Response(
          JSON.stringify({
            success: false,
            error: `Unknown action: ${action}`
          }),
          {
            status: 400,
            headers: { ...corsHeaders, "Content-Type": "application/json" }
          }
        );
    }

    // Delete the report after resolving
    const { error: deleteReportError } = await supabase
      .from("reports")
      .delete()
      .eq("id", report_id);

    if (deleteReportError) {
      console.error("Error deleting report:", deleteReportError);
      // Don't fail the request if we can't delete the report
      // The action was already performed
    }

    // Optionally log the resolution action in an audit log
    // (This would be a separate audit_log table if you have one)
    // await supabase.from("audit_log").insert({
    //   action: "resolve_report",
    //   report_id,
    //   deal_id: report.deal_id,
    //   user_id,
    //   resolution_action: action,
    //   reason,
    //   timestamp: new Date().toISOString()
    // });

    return new Response(
      JSON.stringify({
        success: true,
        message: `Report resolved: ${actionMessage}`
      }),
      {
        headers: { ...corsHeaders, "Content-Type": "application/json" },
        status: 200
      }
    );
  } catch (error) {
    console.error("Server error:", error);
    return new Response(
      JSON.stringify({
        success: false,
        error: error.message || "Server error"
      }),
      {
        headers: { ...corsHeaders, "Content-Type": "application/json" },
        status: 500
      }
    );
  }
});
