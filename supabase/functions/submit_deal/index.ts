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
    // ✅ CRITICAL: Use SERVICE_ROLE_KEY instead of ANON_KEY
    const supabaseUrl = Deno.env.get('SUPABASE_URL')!
    const supabaseServiceKey = Deno.env.get('SUPABASE_SERVICE_ROLE_KEY')!

    const supabase = createClient(supabaseUrl, supabaseServiceKey, {
      auth: {
        autoRefreshToken: false,
        persistSession: false
      }
    })

    // ✨ CATEGORY CHANGE 1: Parse request body including new fields
    const {
      title,
      description,
      link,
      image_url,
      location,
      category = 'other',      // ✨ NEW: Category field with default fallback
      promo_code = null        // ✨ NEW: Promo code field (optional)
    } = await req.json()

    // Validate required fields
    if (!title || !image_url) {
      return new Response(
        JSON.stringify({
          success: false,
          error: 'Missing required fields: title, image_url'
        }),
        {
          status: 400,
          headers: { ...corsHeaders, 'Content-Type': 'application/json' }
        }
)
}

// Must have either link OR location
if (!link && !location) {
      return new Response(
        JSON.stringify({
          success: false,
          error: 'Must provide either link or location'
        }),
        {
          status: 400,
          headers: { ...corsHeaders, 'Content-Type': 'application/json' }
        }
)
}

// ✨ CATEGORY CHANGE 2: Validate category value (optional but recommended)
const validCategories = ['food_dining', 'shopping_fashion', 'entertainment', 'home_services', 'other']
const finalCategory = validCategories.includes(category) ? category : 'other'

// ✨ CATEGORY CHANGE 3: Insert deal with category and promo_code
const { data, error } = await supabase
.from('deals')
      .insert({
        title,
        description,
        link: link || null,
        image_url,
        location: location || null,
        category: finalCategory,        // ✨ NEW: Include category in INSERT
        promo_code: promo_code || null, // ✨ NEW: Include promo_code in INSERT
        status: 'pending',
        hot_count: 0,
        cold_count: 0
      })
      .select()

    if (error) {
      console.error('Database error:', error)
      return new Response(
        JSON.stringify({
          success: false,
          error: 'Failed to submit deal',
          details: error.message
        }),
        {
          status: 500,
          headers: { ...corsHeaders, 'Content-Type': 'application/json' }
        }
)
}

// ✨ CATEGORY CHANGE 4: Log successful submission with category
console.log(`✅ Deal submitted: "${title}" | Category: ${finalCategory}`)

    // Return success response
    return new Response(
      JSON.stringify({
        success: true,
        message: '✅ Deal submitted',
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