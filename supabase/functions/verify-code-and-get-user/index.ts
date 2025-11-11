import { serve } from "https://deno.land/std@0.168.0/http/server.ts";
import { createClient } from 'https://esm.sh/@supabase/supabase-js@2';
const corsHeaders = {
  'Access-Control-Allow-Origin': '*',
  'Access-Control-Allow-Headers': 'authorization, x-client-info, apikey, content-type'
};
// Username generation
function generateUsername() {
  const adjectives = [
    'Hunter',
    'Hero',
    'Warrior',
    'Scout',
    'Finder',
    'Master',
    'Pro',
    'Expert',
    'Ninja',
    'Legend',
    'Guru',
    'Wizard',
    'Champion',
    'Star',
    'King',
    'Queen',
    'Boss',
    'Chief',
    'Captain',
    'Ace',
    'Elite',
    'Prime',
    'Supreme',
    'Ultra',
    'Mega'
  ];
  const adjective = adjectives[Math.floor(Math.random() * adjectives.length)];
  const number = Math.floor(Math.random() * 900) + 100;
  return `Deal${adjective}${number}`;
}
serve(async (req)=>{
  if (req.method === 'OPTIONS') {
    return new Response('ok', {
      headers: corsHeaders
    });
  }
  try {
    const supabaseUrl = Deno.env.get('SUPABASE_URL');
    const supabaseServiceKey = Deno.env.get('SUPABASE_SERVICE_ROLE_KEY');
    const supabaseAnonKey = Deno.env.get('SUPABASE_ANON_KEY');
    const { email, code, device_id } = await req.json();
    if (!email || !code || !device_id) {
      return new Response(JSON.stringify({
        success: false,
        error: 'Missing email, code, or device_id'
      }), {
        status: 400,
        headers: {
          ...corsHeaders,
          'Content-Type': 'application/json'
        }
      });
    }
    // ========================================
    // ✅ FIX: Use ANON client for Auth verification
    // ========================================
    const supabaseAuth = createClient(supabaseUrl, supabaseAnonKey);
    console.log(`Verifying code for: ${email}`);
    const { data: authData, error: authError } = await supabaseAuth.auth.verifyOtp({
      email: email,
      token: code,
      type: 'email'
    });
    if (authError) {
      console.error('OTP verification failed:', authError.message);
      return new Response(JSON.stringify({
        success: false,
        error: 'Invalid or expired code. Please try again.'
      }), {
        status: 401,
        headers: {
          ...corsHeaders,
          'Content-Type': 'application/json'
        }
      });
    }
    console.log('✅ Code verified successfully');
    // ========================================
    // ✅ FIX: Use SERVICE client for database operations
    // ========================================
    const supabaseAdmin = createClient(supabaseUrl, supabaseServiceKey, {
      auth: {
        autoRefreshToken: false,
        persistSession: false
      }
    });
    // Check if user exists
    const { data: existingUser, error: userError } = await supabaseAdmin.from('users').select('*').eq('email', email).maybeSingle();
    let user = existingUser;
    if (!existingUser) {
      // Generate unique username
      let username = generateUsername();
      let attempts = 0;
      while(attempts < 10){
        const { data: usernameCheck } = await supabaseAdmin.from('users').select('username').eq('username', username).maybeSingle();
        if (!usernameCheck) break;
        username = generateUsername();
        attempts++;
      }
      if (attempts >= 10) {
        username = `${generateUsername()}_${Date.now() % 10000}`;
      }
      // Create new user
      const { data: newUser, error: createError } = await supabaseAdmin.from('users').insert({
        email: email,
        username: username,
        device_id: device_id,
        email_verified: true,
        created_at: new Date().toISOString(),
        last_login_at: new Date().toISOString(),
        total_deals_posted: 0,
        approved_deals_count: 0,
        rejected_deals_count: 0,
        trust_level: 'new'
      }).select().single();
      if (createError) {
        console.error('Create user error:', createError);
        return new Response(JSON.stringify({
          success: false,
          error: 'Failed to create account. Please try again.'
        }), {
          status: 500,
          headers: {
            ...corsHeaders,
            'Content-Type': 'application/json'
          }
        });
      }
      user = newUser;
      console.log(`✅ New user created: ${username} (${email})`);
    } else {
      // Update existing user
      const { error: updateError } = await supabaseAdmin.from('users').update({
        device_id: device_id,
        last_login_at: new Date().toISOString()
      }).eq('email', email);
      if (updateError) {
        console.error('Update user error:', updateError);
      }
      console.log(`👋 Returning user: ${existingUser.username} (${email})`);
    }
    return new Response(JSON.stringify({
      success: true,
      message: existingUser ? '👋 Welcome back!' : '🎉 Account created!',
      user: {
        id: user.id,
        email: user.email,
        username: user.username,
        is_new: !existingUser
      }
    }), {
      status: 200,
      headers: {
        ...corsHeaders,
        'Content-Type': 'application/json'
      }
    });
  } catch (error) {
    console.error('Error:', error);
    return new Response(JSON.stringify({
      success: false,
      error: 'Server error. Please try again.',
      details: error.message
    }), {
      status: 500,
      headers: {
        ...corsHeaders,
        'Content-Type': 'application/json'
      }
    });
  }
});
