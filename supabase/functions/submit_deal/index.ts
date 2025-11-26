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
    const { title, description, link, image_url, location, category = 'other', promo_code = null, posted_by = 'Anonymous',expires_in_days = 10, user_id = null, device_id = null, original_price = null, discounted_price = null } = await req.json();
    // Validate required fields
    if (!title || !image_url) {
      return new Response(JSON.stringify({
        success: false,
        error: 'Missing required fields: title, image_url'
      }), {
        status: 400,
        headers: {
          ...corsHeaders,
          'Content-Type': 'application/json'
        }
      });
    }
    // Must have either link OR location
    if (!link && !location) {
      return new Response(JSON.stringify({
        success: false,
        error: 'Must provide either link or location'
      }), {
        status: 400,
        headers: {
          ...corsHeaders,
          'Content-Type': 'application/json'
        }
      });
    }
    // Validate category
    const validCategories = [
      'food_dining',
      'shopping_fashion',
      'entertainment',
      'home_services',
      'other'
    ];
    const finalCategory = validCategories.includes(category) ? category : 'other';

    // ✨ NEW: Calculate expiration date
    const expiresAt = new Date();
    expiresAt.setDate(expiresAt.getDate() + expires_in_days);
console.log(`✅ Deal will expire at: ${expiresAt.toISOString()} (in ${expires_in_days} days)`);

    // AUTO-APPROVAL LOGIC
    let dealStatus = 'pending';
    let autoApproved = false;
    let requiresReview = true;
    let userRole = 'user';
    let userAutoApprove = false;
    // If userId provided, check user's role and auto_approve privilege
    if (user_id) {
      const { data: user, error: userError } = await supabase.from('users').select('role, auto_approve').eq('id', user_id).single();
      if (user && !userError) {
        userRole = user.role;
        userAutoApprove = user.auto_approve;
        console.log(`User: ${posted_by} | Role: ${userRole} | Auto-approve: ${userAutoApprove}`);

        // RULE 1: ADMINS ALWAYS AUTO-APPROVE
        if (userRole === 'admin') {
          dealStatus = 'approved';
          autoApproved = true;
          requiresReview = false;
          console.log('ADMIN: Deal auto-approved');
        } else if (userRole === 'moderator') {
          dealStatus = 'approved';
          autoApproved = true;
          requiresReview = false;
          console.log('MODERATOR: Deal auto-approved');
        } else if (userAutoApprove === true) {
          const randomReviewChance = Math.random();
          if (randomReviewChance < 0.15) {
            // 15% chance: Send to review even for trusted users
            dealStatus = 'pending';
            autoApproved = false;
            requiresReview = true;
            console.log('TRUSTED USER: Random review triggered (15% chance)');
          } else {
            // 85% chance: Auto-approve
            dealStatus = 'approved';
            autoApproved = true;
            requiresReview = false;
            console.log('TRUSTED USER: Deal auto-approved');
          }
        } else {
          dealStatus = 'pending';
          autoApproved = false;
          requiresReview = true;
          console.log('NEW USER: Deal requires review');
        }
      } else {
        console.warn('User not found, defaulting to pending');
      }
    } else {
      console.log('No user_id provided, defaulting to pending');
    }
    // INSERT DEAL WITH PROPER STATUS
    const dealData = {
      title,
      description,
      link: link || null,
      image_url,
      location: location || null,
      category: finalCategory,
      promo_code: promo_code || null,
      posted_by: posted_by || 'Anonymous',
      expires_at: expiresAt.toISOString(),  // ✨ NEW: Added this line
      original_price: original_price || null,      // ✨ NEW: Price fields (2025-11-16)
      discounted_price: discounted_price || null,  // ✨ NEW: Price fields (2025-11-16)
      status: dealStatus,
      auto_approved: autoApproved,
      requires_review: requiresReview,
      hot_count: 0,
      cold_count: 0
    };
    // Add user tracking if userId provided
    if (user_id) {
      dealData.submitted_by_user_id = user_id;
    }
    if (device_id) {
      dealData.submitted_by_device = device_id;
    }
    // If auto-approved, set approved_at timestamp
    if (autoApproved) {
      dealData.approved_at = new Date().toISOString();
      if (user_id) {
        dealData.approved_by = user_id;
      }
    }
    const { data, error } = await supabase.from('deals').insert(dealData).select();
    if (error) {
      console.error('Database error:', error);
      return new Response(JSON.stringify({
        success: false,
        error: 'Failed to submit deal',
        details: error.message
      }), {
        status: 500,
        headers: {
          ...corsHeaders,
          'Content-Type': 'application/json'
        }
      });
    }
    console.log(`Deal submitted: "${title}" | Status: ${dealStatus} | Category: ${finalCategory}`);

    // ✅ NEW: Send push notification if deal was auto-approved (2025-11-25)
    if (autoApproved && data && data.length > 0) {
      try {
        const dealId = data[0].id;
        console.log('📨 Sending notification for auto-approved deal:', dealId);

        const notificationUrl = `${supabaseUrl}/functions/v1/send_notification`;
        const notificationResponse = await fetch(notificationUrl, {
          method: 'POST',
          headers: {
            'Content-Type': 'application/json',
            'Authorization': `Bearer ${supabaseServiceKey}`
          },
          body: JSON.stringify({
            dealId: dealId,
            title: title,
            category: finalCategory,
            imageUrl: image_url,
            type: 'new_deal'
          })
        });

        if (!notificationResponse.ok) {
          const notifError = await notificationResponse.text();
          console.error('❌ Failed to send notification:', notifError);
          // Don't fail the deal submission if notification fails
        } else {
          console.log('✅ Push notification sent successfully');
        }
      } catch (notifError) {
        console.error('❌ Error sending notification:', notifError);
        // Don't fail the deal submission if notification fails
      }
    }

    return new Response(JSON.stringify({
      success: true,
      message: autoApproved ? 'Deal submitted and approved!' : 'Deal submitted for review',
      data
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
