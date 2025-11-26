# 🚀 Notification System - Quick Start Guide

## ✅ Implementation Status

### **Frontend (100% Complete):**
- ✅ NotificationSettingsScreen with toggles
- ✅ FCM subscription management
- ✅ Permission handling (Android 13+)
- ✅ Notification display service
- ✅ Navigation wired up

### **Backend (Needs Configuration):**
- ✅ Edge Functions created
- ⚠️ **Needs Firebase credentials** (5 minutes)
- ⚠️ **Needs deployment** (2 minutes)

---

## 🔥 5-Minute Deployment

### **Step 1: Get Firebase Service Account (2 min)**

1. Go to [Firebase Console](https://console.firebase.google.com/)
2. Your Project → ⚙️ Settings → **Service Accounts**
3. Click **"Generate New Private Key"**
4. Download `serviceAccountKey.json`

---

### **Step 2: Encode Private Key (1 min)**

**macOS/Linux:**
```bash
cat serviceAccountKey.json | jq -r '.private_key' | base64 | tr -d '\n' > private_key_base64.txt
```

**Or use online tool:**
1. Go to https://www.base64encode.org/
2. Copy entire `private_key` from JSON (including BEGIN/END lines)
3. Encode and copy result

---

### **Step 3: Set Supabase Secrets (1 min)**

```bash
# Option A: CLI (recommended)
supabase secrets set FIREBASE_PROJECT_ID="your-project-id"
supabase secrets set FIREBASE_CLIENT_EMAIL="firebase-adminsdk-xxxxx@your-project.iam.gserviceaccount.com"
supabase secrets set FIREBASE_PRIVATE_KEY="<paste_base64_here>"

# Option B: Dashboard
# Go to Supabase Dashboard → Settings → Edge Functions → Secrets
# Add the 3 secrets manually
```

---

### **Step 4: Deploy Functions (1 min)**

```bash
supabase functions deploy send_notification
supabase functions deploy approve_deal
```

---

### **Step 5: Test! (1 min)**

**Manual Test:**
```bash
curl -X POST 'https://YOUR_PROJECT.supabase.co/functions/v1/send_notification' \
  -H 'Authorization: Bearer YOUR_ANON_KEY' \
  -H 'Content-Type: application/json' \
  -d '{"dealId":"test","title":"Test Deal!","category":"food_dining"}'
```

**Real-World Test:**
1. Open app → Enable notifications
2. Go to Moderator Dashboard
3. Approve a pending deal
4. **Notification appears!** 🎉

---

## 📱 How to Test on Device

### **1. Enable Notifications:**
```
App → 3-dot menu → Notifications → Enable "All Deals"
```

Check Logcat:
```
NotificationManager: ✅ Subscribed to topic: all_deals
```

### **2. Send Test from Firebase Console:**
```
Firebase Console → Cloud Messaging → "Send your first message"

Title: 🔥 Test Deal!
Text: This is a test notification
Target: Topic → all_deals
```

### **3. Or Approve a Real Deal:**
```
App → Moderator Dashboard → Pending Deals → Approve
```

Within 5 seconds → **Notification appears!** 📱

---

## 🔍 Verify Everything Works

### **Check 1: Supabase Logs**
```
Supabase Dashboard → Edge Functions → send_notification → Logs

Should see:
📨 Sending notification for deal: abc123
✅ Sent to topic: all_deals
```

### **Check 2: Device Logcat**
```bash
adb logcat | grep FCMService

Should see:
FCMService: 📨 FCM Message Received
FCMService: ✅ Notification displayed
```

### **Check 3: System Notification**
- Notification appears in system tray ✅
- Shows title and message ✅
- Tapping opens app ✅

---

## 🎯 End-to-End Flow

```
┌─────────────────────────────────────────────────┐
│  Moderator Approves Deal in App                │
└──────────────────┬──────────────────────────────┘
                   │
                   ▼
┌─────────────────────────────────────────────────┐
│  approve_deal Edge Function                     │
│  - Updates deal status to "approved"            │
│  - Calls send_notification function             │
└──────────────────┬──────────────────────────────┘
                   │
                   ▼
┌─────────────────────────────────────────────────┐
│  send_notification Edge Function                │
│  - Gets Firebase credentials from secrets       │
│  - Generates OAuth2 access token                │
│  - Sends to topics:                             │
│    • all_deals                                  │
│    • cat_{category} (e.g., cat_food_dining)     │
└──────────────────┬──────────────────────────────┘
                   │
                   ▼
┌─────────────────────────────────────────────────┐
│  Firebase Cloud Messaging (FCM)                 │
│  - Delivers to subscribed devices               │
│  - Battery-efficient topic messaging            │
└──────────────────┬──────────────────────────────┘
                   │
                   ▼
┌─────────────────────────────────────────────────┐
│  User's Device                                  │
│  - DohaDealsFirebaseMessagingService receives   │
│  - Creates notification channel                 │
│  - Shows in system tray                         │
│  - User taps → Opens app                        │
└─────────────────────────────────────────────────┘
```

---

## 🐛 Common Issues & Fixes

| Issue | Cause | Fix |
|-------|-------|-----|
| "Firebase credentials not configured" | Secrets not set | Run `supabase secrets list` to verify |
| "Failed to get access token" | Wrong private key encoding | Re-encode private key (include BEGIN/END) |
| Notification not received | Not subscribed yet | Wait 30 sec after enabling toggle |
| "401 Unauthorized" | Invalid service account | Regenerate key in Firebase Console |

---

## 📊 Success Metrics

After deployment, monitor:

1. **Firebase Console:**
   - Cloud Messaging → Check sent/delivered counts
   - Should see messages increasing

2. **Supabase Logs:**
   - Edge Functions → send_notification
   - Look for "✅ Notifications sent successfully"

3. **App Analytics:**
   - Track notification open rate
   - Monitor user engagement after notifications

---

## 🎉 You're Done!

The notification system is now fully operational:

✅ **Frontend:** Users can subscribe/unsubscribe
✅ **Backend:** Automatically sends on deal approval
✅ **Firebase:** Delivers to subscribed devices

**Test it now:**
1. Approve a deal → Notification sent! 📱
2. Users with notifications enabled will receive it
3. Category filtering works automatically

---

## 🚀 Next Steps (Optional)

### **Add More Notification Types:**
```typescript
// In send_notification function
if (type === 'price_drop') {
  title = '💰 Price Drop Alert!';
} else if (type === 'expiring_soon') {
  title = '⏰ Deal Expiring Soon!';
}
```

### **Scheduled Digests:**
```sql
-- Daily digest at 6 PM
SELECT cron.schedule(
  'daily-digest',
  '0 18 * * *',
  $$SELECT net.http_post(...)$$
);
```

### **Analytics Dashboard:**
Track which notifications get the most engagement and optimize!

---

Need help? Check `supabase/NOTIFICATION_SETUP.md` for detailed troubleshooting! 🚀
