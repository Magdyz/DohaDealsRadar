import { serve } from "https://deno.land/std@0.168.0/http/server.ts";

/**
 * ========================================
 * ✨ SEND NOTIFICATION EDGE FUNCTION
 * Firebase Cloud Messaging (FCM) sender
 * ========================================
 *
 * Created: 2025-11-25 by @Magdyz
 * Location: supabase/functions/send_notification/index.ts
 *
 * Purpose:
 * - Send push notifications via Firebase Cloud Messaging
 * - Support topic-based messaging (all_deals, cat_{categoryId})
 * - Handle deal approval notifications
 *
 * Environment Variables Required:
 * - FIREBASE_PROJECT_ID: Your Firebase project ID
 * - FIREBASE_PRIVATE_KEY: Firebase service account private key (base64 encoded)
 * - FIREBASE_CLIENT_EMAIL: Firebase service account email
 *
 * Topics:
 * - all_deals: Global notifications for all users
 * - cat_food_dining: Food & Dining category
 * - cat_shopping_fashion: Shopping & Fashion category
 * - cat_entertainment: Entertainment & Leisure category
 * - cat_home_services: Home & Services category
 * - cat_other: Other category
 */

const corsHeaders = {
  'Access-Control-Allow-Origin': '*',
  'Access-Control-Allow-Headers': 'authorization, x-client-info, apikey, content-type'
};

// Category emoji mapping (matches Android app)
const categoryEmojis: Record<string, string> = {
  'food_dining': '🍔',
  'shopping_fashion': '🛍️',
  'entertainment': '🎮',
  'home_services': '🏠',
  'other': '⭐'
};

serve(async (req) => {
  // Handle CORS preflight
  if (req.method === 'OPTIONS') {
    return new Response('ok', { headers: corsHeaders });
  }

  try {
    const { dealId, title, category, imageUrl = null, type = 'new_deal' } = await req.json();

    if (!dealId || !title || !category) {
      return new Response(
        JSON.stringify({
          success: false,
          error: 'Missing required fields: dealId, title, category'
        }),
        {
          status: 400,
          headers: { ...corsHeaders, 'Content-Type': 'application/json' }
        }
      );
    }

    console.log('━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━');
    console.log('📨 Sending notification for deal:', dealId);
    console.log('   Title:', title);
    console.log('   Category:', category);
    console.log('━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━');

    // Get Firebase credentials from environment
    const projectId = Deno.env.get('FIREBASE_PROJECT_ID');
    const privateKeyBase64 = Deno.env.get('FIREBASE_PRIVATE_KEY');
    const clientEmail = Deno.env.get('FIREBASE_CLIENT_EMAIL');

    if (!projectId || !privateKeyBase64 || !clientEmail) {
      console.error('❌ Missing Firebase credentials in environment variables');
      return new Response(
        JSON.stringify({
          success: false,
          error: 'Firebase credentials not configured. Check Supabase secrets.'
        }),
        {
          status: 500,
          headers: { ...corsHeaders, 'Content-Type': 'application/json' }
        }
      );
    }

    // Decode private key from base64
    const privateKey = atob(privateKeyBase64);

    // Get OAuth2 access token
    const accessToken = await getAccessToken(clientEmail, privateKey);

    // Prepare notification data
    const emoji = categoryEmojis[category] || '🔥';
    const truncatedTitle = title.length > 100 ? title.substring(0, 97) + '...' : title;

    // Send to "all_deals" topic
    await sendFCMMessage({
      projectId,
      accessToken,
      topic: 'all_deals',
      title: `${emoji} New Deal in Doha!`,
      body: truncatedTitle,
      dealId,
      category,
      imageUrl
    });

    // Send to category-specific topic
    await sendFCMMessage({
      projectId,
      accessToken,
      topic: `cat_${category}`,
      title: `${emoji} New ${getCategoryName(category)} Deal!`,
      body: truncatedTitle,
      dealId,
      category,
      imageUrl
    });

    console.log('✅ Notifications sent successfully');
    console.log('   Topics: all_deals, cat_' + category);
    console.log('━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━');

    return new Response(
      JSON.stringify({
        success: true,
        message: 'Notifications sent',
        topics: ['all_deals', `cat_${category}`]
      }),
      {
        status: 200,
        headers: { ...corsHeaders, 'Content-Type': 'application/json' }
      }
    );
  } catch (error) {
    console.error('❌ Error sending notification:', error);
    return new Response(
      JSON.stringify({
        success: false,
        error: 'Failed to send notification',
        details: error.message
      }),
      {
        status: 500,
        headers: { ...corsHeaders, 'Content-Type': 'application/json' }
      }
    );
  }
});

/**
 * Get OAuth2 access token for Firebase Admin SDK
 */
async function getAccessToken(clientEmail: string, privateKey: string): Promise<string> {
  const scope = 'https://www.googleapis.com/auth/firebase.messaging';

  // Create JWT
  const header = {
    alg: 'RS256',
    typ: 'JWT'
  };

  const now = Math.floor(Date.now() / 1000);
  const payload = {
    iss: clientEmail,
    scope: scope,
    aud: 'https://oauth2.googleapis.com/token',
    exp: now + 3600,
    iat: now
  };

  // Encode header and payload
  const encodedHeader = base64UrlEncode(JSON.stringify(header));
  const encodedPayload = base64UrlEncode(JSON.stringify(payload));
  const unsignedToken = `${encodedHeader}.${encodedPayload}`;

  // Sign with private key
  const signature = await signRS256(unsignedToken, privateKey);
  const jwt = `${unsignedToken}.${signature}`;

  // Exchange JWT for access token
  const response = await fetch('https://oauth2.googleapis.com/token', {
    method: 'POST',
    headers: { 'Content-Type': 'application/x-www-form-urlencoded' },
    body: new URLSearchParams({
      grant_type: 'urn:ietf:params:oauth:grant-type:jwt-bearer',
      assertion: jwt
    })
  });

  const data = await response.json();

  if (!response.ok) {
    throw new Error(`Failed to get access token: ${JSON.stringify(data)}`);
  }

  return data.access_token;
}

/**
 * Sign data with RS256 algorithm
 */
async function signRS256(data: string, privateKey: string): Promise<string> {
  const encoder = new TextEncoder();
  const dataBuffer = encoder.encode(data);

  // Import private key
  const key = await crypto.subtle.importKey(
    'pkcs8',
    pemToArrayBuffer(privateKey),
    {
      name: 'RSASSA-PKCS1-v1_5',
      hash: 'SHA-256'
    },
    false,
    ['sign']
  );

  // Sign
  const signature = await crypto.subtle.sign(
    'RSASSA-PKCS1-v1_5',
    key,
    dataBuffer
  );

  return base64UrlEncode(signature);
}

/**
 * Convert PEM to ArrayBuffer
 */
function pemToArrayBuffer(pem: string): ArrayBuffer {
  const pemContents = pem
    .replace(/-----BEGIN PRIVATE KEY-----/, '')
    .replace(/-----END PRIVATE KEY-----/, '')
    .replace(/\s/g, '');
  const binaryString = atob(pemContents);
  const bytes = new Uint8Array(binaryString.length);
  for (let i = 0; i < binaryString.length; i++) {
    bytes[i] = binaryString.charCodeAt(i);
  }
  return bytes.buffer;
}

/**
 * Base64 URL encode
 */
function base64UrlEncode(data: string | ArrayBuffer): string {
  let base64: string;

  if (typeof data === 'string') {
    base64 = btoa(data);
  } else {
    const bytes = new Uint8Array(data);
    let binary = '';
    for (let i = 0; i < bytes.length; i++) {
      binary += String.fromCharCode(bytes[i]);
    }
    base64 = btoa(binary);
  }

  return base64
    .replace(/\+/g, '-')
    .replace(/\//g, '_')
    .replace(/=/g, '');
}

/**
 * Send FCM message to topic
 */
async function sendFCMMessage({
  projectId,
  accessToken,
  topic,
  title,
  body,
  dealId,
  category,
  imageUrl
}: {
  projectId: string;
  accessToken: string;
  topic: string;
  title: string;
  body: string;
  dealId: string;
  category: string;
  imageUrl: string | null;
}): Promise<void> {
  const url = `https://fcm.googleapis.com/v1/projects/${projectId}/messages:send`;

  const message = {
    message: {
      topic: topic,
      notification: {
        title: title,
        body: body,
        ...(imageUrl && { image: imageUrl })  // ✅ Add image if provided
      },
      data: {
        dealId: dealId,
        category: category,
        imageUrl: imageUrl || '',  // ✅ Pass image URL in data payload
        action: 'view_deal'
      },
      android: {
        priority: 'high',
        notification: {
          sound: 'default',
          channel_id: 'deals_channel',
          ...(imageUrl && { image: imageUrl })  // ✅ Android-specific image
          // ✅ Removed click_action - not needed for native Android
          // The PendingIntent in FirebaseMessagingService handles taps
        }
      }
    }
  };

  const response = await fetch(url, {
    method: 'POST',
    headers: {
      'Authorization': `Bearer ${accessToken}`,
      'Content-Type': 'application/json'
    },
    body: JSON.stringify(message)
  });

  if (!response.ok) {
    const error = await response.text();
    throw new Error(`FCM API error: ${error}`);
  }

  console.log(`✅ Sent to topic: ${topic}`);
}

/**
 * Get human-readable category name
 */
function getCategoryName(categoryId: string): string {
  const names: Record<string, string> = {
    'food_dining': 'Food & Dining',
    'shopping_fashion': 'Shopping & Fashion',
    'entertainment': 'Entertainment',
    'home_services': 'Home & Services',
    'other': 'Other'
  };
  return names[categoryId] || 'New';
}
