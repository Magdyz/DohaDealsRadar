import { serve } from "https://deno.land/std@0.168.0/http/server.ts";

serve(async (req) => {
  try {
    const formData = await req.formData();
    const file = formData.get("file") as File;

    if (!file || !file.type.startsWith("image/")) {
      return new Response("Invalid image file", { status: 400 });
    }

    // Upload to Supabase Storage
    const filename = `${crypto.randomUUID()}.jpg`;
    const supabaseUrl = Deno.env.get("SUPABASE_URL");
    const supabaseKey = Deno.env.get("SUPABASE_SERVICE_ROLE_KEY");

    const uploadRes = await fetch(
      `${supabaseUrl}/storage/v1/object/doha-deals/${filename}`,
      {
        method: "POST",
        headers: {
          "Authorization": `Bearer ${supabaseKey}`,
          "Content-Type": "image/jpeg",
        },
        body: await file.arrayBuffer(),
      }
    );

    if (!uploadRes.ok) throw new Error("Upload failed");

    return new Response(
      JSON.stringify({ url: `${supabaseUrl}/storage/v1/object/public/doha-deals/${filename}` }),
      { headers: { "Content-Type": "application/json" } }
    );
  } catch (e) {
    return new Response(e.message, { status: 500 });
  }
});
