import { serve } from "https://deno.land/std@0.168.0/http/server.ts";
import { createClient } from "https://esm.sh/@supabase/supabase-js@2";

/**
 * Dismiss a report without taking action
 * Marks the report as reviewed but no action needed
 *
 * CREATED: 2025-11-22
 *
 * Required: report_id, user_id (moderator/admin)
 * Optional: reason
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

    const { report_id, user_id, reason } = await req.json();

    if (!report_id || !user_id) {
      return new Response(
        JSON.stringify({
          success: false,
          error: "Missing required fields: report_id, user_id"
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
          error: "Unauthorized: Only moderators/admins can dismiss reports"
        }),
        {
          status: 403,
          headers: { ...corsHeaders, "Content-Type": "application/json" }
        }
      );
    }

    // Check if report exists
    const { data: report, error: fetchError } = await supabase
      .from("reports")
      .select("id")
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

    // Delete the report (dismissing it)
    // Note: You could also update a status field if you want to keep dismissed reports
    const { error: deleteError } = await supabase
      .from("reports")
      .delete()
      .eq("id", report_id);

    if (deleteError) {
      console.error("Error dismissing report:", deleteError);
      throw deleteError;
    }

    // Optionally log the dismissal action in an audit log
    // (This would be a separate audit_log table if you have one)
    // await supabase.from("audit_log").insert({
    //   action: "dismiss_report",
    //   report_id,
    //   user_id,
    //   reason,
    //   timestamp: new Date().toISOString()
    // });

    return new Response(
      JSON.stringify({
        success: true,
        message: "Report dismissed successfully"
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
