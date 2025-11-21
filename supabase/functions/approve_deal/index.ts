import { serve } from "https://deno.land/std@0.168.0/http/server.ts";
import { createClient } from 'https://esm.sh/@supabase/supabase-js@2';
const corsHeaders = {
  'Access-Control-Allow-Origin': '*',
  'Access-Control-Allow-Headers': 'authorization, x-client-info, apikey, content-type'
};
serve(async (req)=>{
  if (req.method === 'OPTIONS') {
    return new Response('ok', {
      headers: corsHeaders
    });
  }
  try {
    const supabaseUrl = Deno.env.get('SUPABASE_URL');
    const supabaseServiceKey = Deno.env.get('SUPABASE_SERVICE_ROLE_KEY');
    const supabase = createClient(supabaseUrl, supabaseServiceKey, {
      auth: {
        autoRefreshToken: false,
        persistSession: false
      }
    });
    const { deal_id, moderator_user_id } = await req.json();
    if (!deal_id) {
      return new Response(JSON.stringify({
        success: false,
        error: 'Missing deal_id'
      }), {
        status: 400,
        headers: {
          ...corsHeaders,
          'Content-Type': 'application/json'
        }
      });
    }
    const { data: deal, error: fetchError } = await supabase.from('deals').select('submitted_by_user_id, title, posted_by, status').eq('id', deal_id).single();
    if (fetchError || !deal) {
      return new Response(JSON.stringify({
        success: false,
        error: 'Deal not found'
      }), {
        status: 404,
        headers: {
          ...corsHeaders,
          'Content-Type': 'application/json'
        }
      });
    }
    if (deal.status === 'approved') {
      // Fetch the full deal data to return
      const { data: approvedDeal } = await supabase.from('deals').select('*').eq('id', deal_id).single();
      return new Response(JSON.stringify({
        success: true,
        message: 'Deal already approved',
        data: approvedDeal
      }), {
        status: 200,
        headers: {
          ...corsHeaders,
          'Content-Type': 'application/json'
        }
      });
    }
    // Update the deal to approved status
    const { error: updateError } = await supabase.from('deals').update({
      status: 'approved',
      approved_by: moderator_user_id || null,
      approved_at: new Date().toISOString()
    }).eq('id', deal_id);
    if (updateError) {
      return new Response(JSON.stringify({
        success: false,
        error: 'Failed to approve deal',
        details: updateError.message
      }), {
        status: 500,
        headers: {
          ...corsHeaders,
          'Content-Type': 'application/json'
        }
      });
    }
    // Increment approved deals count for the user
    if (deal.submitted_by_user_id) {
      const { error: rpcError } = await supabase.rpc('increment_approved_deals', {
        p_user_id: deal.submitted_by_user_id
      });
      if (rpcError) {
        console.error('Failed to increment count:', rpcError);
      }
    }
    // Fetch the updated deal to return in response
    const { data: updatedDeal, error: fetchUpdatedError } = await supabase.from('deals').select('*').eq('id', deal_id).single();
    if (fetchUpdatedError) {
      console.error('Failed to fetch updated deal:', fetchUpdatedError);
      return new Response(JSON.stringify({
        success: true,
        message: 'Deal approved successfully but failed to fetch updated data',
        error: fetchUpdatedError.message
      }), {
        status: 200,
        headers: {
          ...corsHeaders,
          'Content-Type': 'application/json'
        }
      });
    }
    return new Response(JSON.stringify({
      success: true,
      message: 'Deal approved successfully',
      data: updatedDeal
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
      error: 'Server error',
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
