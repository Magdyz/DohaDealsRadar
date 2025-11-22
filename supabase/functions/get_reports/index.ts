import { serve } from "https://deno.land/std@0.168.0/http/server.ts";
import { createClient } from "https://esm.sh/@supabase/supabase-js@2";

/**
 * Get all submitted reports with details
 * Returns reports with joined deal and user information
 *
 * CREATED: 2025-11-22
 *
 * Required: user_id (moderator/admin)
 * Optional: page, limit
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

    const { user_id, page = 1, limit = 20 } = await req.json();

    if (!user_id) {
      return new Response(
        JSON.stringify({
          success: false,
          error: "Missing user_id"
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
      p_permission: 'view_reports'
    });

    if (!hasPermission) {
      return new Response(
        JSON.stringify({
          success: false,
          error: "Unauthorized: Only moderators/admins can view reports"
        }),
        {
          status: 403,
          headers: { ...corsHeaders, "Content-Type": "application/json" }
        }
      );
    }

    // Calculate pagination offset
    const offset = (page - 1) * limit;

    // Fetch reports with JOIN on deals table only
    // User information will be fetched separately
    // Using explicit foreign key relationship name to avoid ambiguity
    const { data: reports, error } = await supabase
      .from("reports")
      .select(`
        id,
        deal_id,
        device_id,
        reason,
        note,
        created_at,
        deals!reports_deal_id_fkey!inner (
          title,
          image_url,
          category,
          status,
          posted_by
        )
      `)
      .order("created_at", { ascending: false })
      .range(offset, offset + limit - 1);

    if (error) {
      console.error("Error fetching reports:", error);
      throw error;
    }

    // Transform the data and fetch user information for each report
    const transformedReports = await Promise.all(
      reports.map(async (report: any) => {
        // Try to find user by device_id
        const { data: user } = await supabase
          .from("users")
          .select("username, email, role, approved_deals_count")
          .eq("device_id", report.device_id)
          .maybeSingle();  // Use maybeSingle to avoid error if not found

        return {
          id: report.id,
          deal_id: report.deal_id,
          device_id: report.device_id,
          reason: report.reason,
          note: report.note,
          created_at: report.created_at,
          // Deal information
          deal_title: report.deals?.title,
          deal_image: report.deals?.image_url,
          deal_category: report.deals?.category,
          deal_status: report.deals?.status,
          deal_posted_by: report.deals?.posted_by,
          // Reporter information (null if user not found)
          reporter_username: user?.username || null,
          reporter_email: user?.email || null,
          reporter_role: user?.role || null,
          reporter_approved_deals_count: user?.approved_deals_count || null
        };
      })
    );

    return new Response(
      JSON.stringify({
        success: true,
        data: transformedReports || [],
        count: transformedReports?.length || 0,
        pagination: {
          page,
          limit,
          hasMore: transformedReports.length === limit
        }
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
