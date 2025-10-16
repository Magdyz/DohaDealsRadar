import { serve } from "https://deno.land/std@0.168.0/http/server.ts"
import { createClient } from 'https://esm.sh/@supabase/supabase-js@2'

const corsHeaders = {
'Access-Control-Allow-Origin': '*',
'Access-Control-Allow-Headers': 'authorization, x-client-info, apikey, content-type',
}

serve(async (req) => {
  // Handle CORS preflight
  if (req.method === 'OPTIONS') {
    return new Response('ok', { headers: corsHeaders })
  }

  try {
    const supabaseUrl = Deno.env.get('SUPABASE_URL')!
    const supabaseServiceKey = Deno.env.get('SUPABASE_SERVICE_ROLE_KEY')!

    const supabase = createClient(supabaseUrl, supabaseServiceKey, {
      auth: {
        autoRefreshToken: false,
        persistSession: false
      }
    })

    // Parse request body
    const { deal_id, image_url } = await req.json()

    // Validate required fields
    if (!deal_id || !image_url) {
      return new Response(
        JSON.stringify({
          success: false,
          error: 'Missing required fields: deal_id, image_url'
        }),
        {
          status: 400,
          headers: { ...corsHeaders, 'Content-Type': 'application/json' }
        }
)
}

// Update deal image URL
const { data, error } = await supabase
.from('deals')
      .update({ image_url })
      .eq('id', deal_id)
      .select()
      .single()

    if (error) {
      console.error('Database error:', error)
      return new Response(
        JSON.stringify({
          success: false,
          error: 'Failed to update image',
          details: error.message
        }),
        {
          status: 500,
          headers: { ...corsHeaders, 'Content-Type': 'application/json' }
        }
)
}

// Return success response
return new Response(
      JSON.stringify({
        success: true,
        message: '✅ Image updated',
        data
      }),
      {
        status: 200,
        headers: { ...corsHeaders, 'Content-Type': 'application/json' }
      }
)

} catch (error) {
console.error('Error:', error)
    return new Response(
      JSON.stringify({
        success: false,
        error: 'Server error',
        details: error.message
      }),
      {
        status: 500,
        headers: { ...corsHeaders, 'Content-Type': 'application/json' }
      }
)
}
})
```

---

## 📊 Expected Performance

### **Before (Current):**
```
Compression: 6-7 seconds
Upload: 6-7 seconds
TOTAL: 12-14 seconds ❌
```

### **After (Two-Stage):**
```
Stage 1: Compress both (~700ms)
Stage 2: Upload thumbnail (~1 second)
Stage 3: Submit deal (~300ms)
USER SEES "POSTED!": 2 seconds ✅
Stage 4: Full image uploads in background (user already left screen)