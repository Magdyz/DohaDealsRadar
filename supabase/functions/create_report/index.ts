// supabase/functions/create_report/index.ts
import { serve } from "https://deno.land/std@0.177.0/http/server.ts";
import { createClient } from "https://esm.sh/@supabase/supabase-js@2";

const supabaseUrl = Deno.env.get("SUPABASE_URL");
const supabaseKey = Deno.env.get("SUPABASE_SERVICE_ROLE_KEY");
const supabase = createClient(supabaseUrl, supabaseKey);

serve(async (req) => {
  try {
    const body = await req.json();
    const { deal_id, reason, note, device_id } = body;

    // ✅ Validation: require deal_id and reason
    if (!deal_id || !reason) {
      return new Response(
        JSON.stringify({ error: "Missing required fields: deal_id or reason" }),
        { status: 400 }
      );
    }

    // ✅ Normalize reason to lowercase (Postgres ENUM is case-sensitive)
    const normalizedReason = reason.toLowerCase();

    // ✅ Optional fields: fallback to null or "anonymous"
    const { data, error } = await supabase
      .from("reports")
      .insert([
        {
          deal_id,
          reason: normalizedReason,
          note: note || null,
          device_id: device_id || "anonymous",
          created_at: new Date().toISOString(),
        },
      ])
      .select();

    if (error) throw error;

    return new Response(
      JSON.stringify({ message: "✅ Report submitted", data }),
      { status: 200 }
    );
  } catch (e) {
    return new Response(
      JSON.stringify({ error: "Server error", details: e.message }),
      { status: 500 }
    );
  }
});
