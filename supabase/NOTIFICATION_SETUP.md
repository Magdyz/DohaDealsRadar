# 🔔 Backend Notification Setup Guide

## ✅ What's Been Implemented

### Files Created/Modified:
1. **`supabase/functions/send_notification/index.ts`** - New Edge Function for sending FCM notifications
2. **`supabase/functions/approve_deal/index.ts`** - Modified to automatically send notifications

### How It Works:
```
Deal Posted → Moderator Approves → approve_deal function
                                          ↓
                                  send_notification function
                                          ↓
                              Firebase Cloud Messaging (FCM)
                                          ↓
                            📱 User's Device (Push Notification)
```

---

## 🔧 Setup Instructions

### **Step 1: Get Firebase Service Account Credentials**

1. Go to [Firebase Console](https://console.firebase.google.com/)
2. Select your project
3. Click the **gear icon** ⚙️ → **Project Settings**
4. Go to **Service Accounts** tab
5. Click **"Generate New Private Key"**
6. Download the JSON file (e.g., `serviceAccountKey.json`)

The file looks like this:
```json
{
  "type": "service_account",
  "project_id": "your-project-id",
  "private_key_id": "...",
  "private_key": "-----BEGIN PRIVATE KEY-----\nMIIEvQIBA...\n-----END PRIVATE KEY-----\n",
  "client_email": "firebase-adminsdk-xxxxx@your-project.iam.gserviceaccount.com",
  "client_id": "...",
  "auth_uri": "https://accounts.google.com/o/oauth2/auth",
  "token_uri": "https://oauth2.googleapis.com/token",
  "auth_provider_x509_cert_url": "https://www.googleapis.com/oauth2/v1/certs",
  "client_x509_cert_url": "..."
}
```

---

### **Step 2: Add Secrets to Supabase**

You need to add 3 secrets to Supabase. Here's how:

#### **Option A: Using Supabase Dashboard (Easiest)**

1. Go to [Supabase Dashboard](https://supabase.com/dashboard)
2. Select your project
3. Go to **Settings** → **Edge Functions** → **Secrets**
4. Add these 3 secrets:

| Secret Name | Value | Example |
|-------------|-------|---------|
| `FIREBASE_PROJECT_ID` | Your Firebase project ID | `doha-deals-radar` |
| `FIREBASE_CLIENT_EMAIL` | Service account email | `firebase-adminsdk-xxxxx@doha-deals-radar.iam.gserviceaccount.com` |
| `FIREBASE_PRIVATE_KEY` | Base64 encoded private key | (see below) |

#### **Option B: Using Supabase CLI**

```bash
# Install Supabase CLI if you haven't
npm install -g supabase

# Login to Supabase
supabase login

# Link your project
supabase link --project-ref YOUR_PROJECT_REF

# Set secrets
supabase secrets set FIREBASE_PROJECT_ID="your-project-id"
supabase secrets set FIREBASE_CLIENT_EMAIL="firebase-adminsdk-xxxxx@your-project.iam.gserviceaccount.com"
supabase secrets set FIREBASE_PRIVATE_KEY="<base64_encoded_private_key>"
```

---

### **Step 3: Encode Private Key to Base64**

The `private_key` from your service account JSON needs to be base64 encoded.

#### **On macOS/Linux:**
```bash
# Extract private key from JSON and encode
cat serviceAccountKey.json | jq -r '.private_key' | base64 | tr -d '\n'
```

#### **On Windows (PowerShell):**
```powershell
$json = Get-Content serviceAccountKey.json | ConvertFrom-Json
$bytes = [System.Text.Encoding]::UTF8.GetBytes($json.private_key)
[Convert]::ToBase64String($bytes)
```

#### **Manual Method:**
1. Open `serviceAccountKey.json`
2. Copy the entire `private_key` value (including `-----BEGIN PRIVATE KEY-----` and `-----END PRIVATE KEY-----`)
3. Go to https://www.base64encode.org/
4. Paste the private key
5. Click "Encode"
6. Copy the result

The encoded key will look like:
```
LS0tLS1CRUdJTiBQUklWQVRFIEtFWS0tLS0tCk1JSUV2UUlCQURBTkJna3Foa2lH...
```

Use this value for `FIREBASE_PRIVATE_KEY`.

---

### **Step 4: Deploy Edge Function**

```bash
# Deploy the send_notification function
supabase functions deploy send_notification

# Deploy the updated approve_deal function
supabase functions deploy approve_deal
```

You should see:
```
✅ Deployed Function send_notification
✅ Deployed Function approve_deal
```

---

### **Step 5: Test the Setup**

#### **Test 1: Call send_notification Directly**

```bash
curl -X POST 'https://YOUR_PROJECT_REF.supabase.co/functions/v1/send_notification' \
  -H 'Authorization: Bearer YOUR_SUPABASE_ANON_KEY' \
  -H 'Content-Type: application/json' \
  -d '{
    "dealId": "test-123",
    "title": "Test Deal - 50% Off!",
    "category": "food_dining"
  }'
```

Expected response:
```json
{
  "success": true,
  "message": "Notifications sent",
  "topics": ["all_deals", "cat_food_dining"]
}
```

Check your device - you should receive a notification! 📱

#### **Test 2: Approve a Deal (End-to-End)**

1. Open your app
2. Navigate to **Moderator Dashboard** → **Pending Deals**
3. Approve a deal
4. **Within 5 seconds:** Notification should appear on devices with notifications enabled

---

## 🔍 Verify It's Working

### **Check Supabase Logs:**

1. Go to Supabase Dashboard → **Edge Functions** → **send_notification**
2. Check logs for:
```
━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
📨 Sending notification for deal: abc123
   Title: 50% off at The Pearl
   Category: food_dining
━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
✅ Sent to topic: all_deals
✅ Sent to topic: cat_food_dining
✅ Notifications sent successfully
```

### **Check App Logcat:**

```bash
adb logcat | grep FCMService
```

Look for:
```
FCMService: ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
FCMService: 📨 FCM Message Received
FCMService:    From: /topics/all_deals
FCMService:    Notification: 🍔 New Food Deal!
FCMService: ✅ Notification displayed
```

---

## 🐛 Troubleshooting

### **Issue: "Firebase credentials not configured"**

**Cause:** Secrets not set correctly
**Solution:**
```bash
# Verify secrets are set
supabase secrets list

# Should show:
FIREBASE_PROJECT_ID
FIREBASE_CLIENT_EMAIL
FIREBASE_PRIVATE_KEY

# If missing, set them again
supabase secrets set FIREBASE_PROJECT_ID="your-project-id"
```

---

### **Issue: "Failed to get access token"**

**Cause:** Private key encoding issue
**Solution:**
1. Re-encode the private key using the methods above
2. Make sure to include the entire key including headers:
   ```
   -----BEGIN PRIVATE KEY-----
   ...
   -----END PRIVATE KEY-----
   ```
3. Ensure no extra spaces or newlines

---

### **Issue: "FCM API error: 401 Unauthorized"**

**Cause:** Invalid service account
**Solution:**
1. Regenerate service account key in Firebase Console
2. Download new JSON file
3. Re-encode and update secrets

---

### **Issue: Notification sent but not received on device**

**Checklist:**
- [ ] User has enabled notifications in app settings
- [ ] User is subscribed to the topic (`all_deals` or specific category)
- [ ] Device has internet connection
- [ ] App has notification permissions granted
- [ ] Wait 30 seconds after subscribing (topic propagation delay)

---

## 📊 Monitoring & Analytics

### **Check Firebase Console:**

1. Go to Firebase Console → **Cloud Messaging**
2. You'll see:
   - Messages sent
   - Delivery rate
   - Open rate
   - Errors

### **Log All Notifications:**

Consider adding a `notifications` table in Supabase to track:
- When notifications were sent
- Which topics
- Success/failure
- User engagement

```sql
CREATE TABLE notifications_log (
  id UUID DEFAULT gen_random_uuid() PRIMARY KEY,
  deal_id UUID REFERENCES deals(id),
  topics TEXT[],
  title TEXT,
  sent_at TIMESTAMP DEFAULT NOW(),
  success BOOLEAN,
  error TEXT
);
```

---

## 🚀 Production Checklist

Before deploying to production:

- [ ] All 3 Firebase secrets configured correctly
- [ ] Edge functions deployed and working
- [ ] Test notifications received on real devices
- [ ] Tested all 5 categories
- [ ] Tested global "all_deals" topic
- [ ] Monitored logs for errors
- [ ] Set up Firebase Analytics (optional)
- [ ] Document backend for team

---

## 💡 Tips & Best Practices

### **Rate Limiting:**
Consider adding rate limiting to prevent notification spam:
```typescript
// In send_notification function
const ONE_HOUR = 3600 * 1000;
const recentNotifications = await checkRecentNotifications(dealId);
if (recentNotifications.length > 0) {
  console.log('Skipping - notification sent recently');
  return;
}
```

### **Scheduled Notifications:**
For digest-style notifications (e.g., "5 new deals today"):
1. Create a new Edge Function: `send_daily_digest`
2. Set up a cron job in Supabase:
```sql
SELECT cron.schedule(
  'daily-deals-digest',
  '0 18 * * *',  -- 6 PM daily
  $$SELECT net.http_post(...)$$
);
```

### **Notification Preferences:**
Extend NotificationManager to support:
- Time preferences (only between 9 AM - 9 PM)
- Frequency limits (max 5 per day)
- Price threshold (only deals over 50% off)

---

## 📝 Summary

You're now ready to send push notifications! 🎉

**End-to-End Flow:**
1. ✅ User enables notifications in app
2. ✅ User subscribes to FCM topics
3. ✅ Moderator approves a deal
4. ✅ `approve_deal` calls `send_notification`
5. ✅ `send_notification` calls Firebase FCM API
6. ✅ User receives notification on device

**Next Steps:**
- Test thoroughly with different categories
- Monitor Firebase console for delivery metrics
- Gather user feedback on notification quality
- Iterate and improve based on engagement data

Need help? Check the logs or reach out! 🚀
