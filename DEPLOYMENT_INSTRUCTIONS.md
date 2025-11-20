# 🚀 Deployment Instructions - Voting Fix

## Critical Issue
The voting system code has been fixed in the repository, but the **edge function is not yet deployed** to Supabase. This is causing the error:
```
{"success":false,"error":"insertedVote is not defined"}
```

The vote gets recorded in the database, but the counts don't update because the old edge function crashes before updating `hot_count` and `cold_count`.

---

## ✅ Step 1: Deploy Edge Function (CRITICAL)

This is the most important step - it fixes the vote counting issue.

```bash
# Navigate to project root
cd /home/user/DohaDealsRadar

# Deploy the updated cast_vote edge function
supabase functions deploy cast_vote
```

**What this fixes:**
- Vote counts (`hot_count`, `cold_count`) will now update correctly
- No more `"insertedVote is not defined"` error
- Proper user_id validation and error messages

---

## ✅ Step 2: Apply Database Migrations

You have two new migrations that need to be applied:

### Option A: Using Supabase CLI (Recommended)
```bash
# Apply all pending migrations
supabase db push
```

### Option B: Using Supabase Dashboard
1. Go to your Supabase project dashboard
2. Navigate to **SQL Editor**
3. Run these two migration files in order:

#### Migration 1: Fix Votes Constraint
**File:** `supabase/migrations/20251120_fix_votes_identifier_constraint.sql`

```sql
ALTER TABLE votes DROP CONSTRAINT IF EXISTS votes_identifier_check;

ALTER TABLE votes
ADD CONSTRAINT votes_identifier_check
CHECK (
  user_id IS NOT NULL OR device_id IS NOT NULL
);
```

**What this fixes:**
- Allows votes with user_id (new system) OR device_id (legacy)
- Prevents constraint violation errors when voting

---

#### Migration 2: Clean Up Legacy Votes
**File:** `supabase/migrations/20251120_cleanup_votes_without_user_id.sql`

⚠️ **WARNING**: This migration will delete all votes that don't have a user_id. Make sure you want to do this before running.

**What this does:**
- Removes all legacy votes without user_id
- Properly decrements vote counts on affected deals
- Ensures fresh start with accurate vote counts

**To run:**
- Copy the entire content from `supabase/migrations/20251120_cleanup_votes_without_user_id.sql`
- Paste into Supabase SQL Editor
- Click "Run"

---

## ✅ Step 3: Verify Deployment

After deploying, test the voting system:

### Test Checklist:
1. **Vote as authenticated user**
   - Open the app
   - Ensure you're logged in
   - Click "Hot" or "Cold" on a deal
   - ✅ Vote should record instantly
   - ✅ Count should update on UI (feed + details)
   - ✅ No flickering or double-counting

2. **Vote as anonymous user**
   - Log out
   - Try to vote on a deal
   - ✅ Should show "Please verify you're human" dialog
   - ✅ Can dismiss with "Maybe Later"
   - ✅ Can log in with "Log In (Quick!)"

3. **Duplicate vote prevention**
   - Vote on a deal
   - Try to vote again on the same deal
   - ✅ Should show "You have already voted on this deal"

4. **Check logs**
   - No `"insertedVote is not defined"` errors
   - Should see: `✅ Vote recorded successfully`
   - Vote counts should match database

---

## 📊 What Was Fixed

### Code Changes (Already Committed)
1. **Edge Function (`cast_vote/index.ts`)**
   - ✅ Made `user_id` REQUIRED for all new votes
   - ✅ Added comprehensive logging
   - ✅ Added user validation (checks user exists in DB)
   - ✅ Proper error messages

2. **FeedViewModel.kt**
   - ✅ Fixed optimistic count logic (no more double-counting)
   - ✅ Added 300ms delay before clearing optimistic state
   - ✅ Updated vote status loading to use user-based checking

3. **DetailsViewModel.kt**
   - ✅ Added 300ms delay for consistent behavior with feed
   - ✅ Proper optimistic update and revert logic

4. **Database Migrations**
   - ✅ Fixed votes constraint to allow user_id or device_id
   - ✅ Created cleanup migration for legacy votes

### Deployment Status
- ✅ Code committed to branch: `claude/record-hot-vote-01DXyKecCUMnL9aaCa5ZSX5n`
- ⚠️ Edge function NOT YET DEPLOYED (needs Step 1 above)
- ⚠️ Migrations NOT YET APPLIED (needs Step 2 above)

---

## 🐛 Troubleshooting

### Issue: Still seeing "insertedVote is not defined"
**Solution:** Edge function not deployed yet. Run Step 1.

### Issue: Constraint violation when voting
**Solution:** Run Migration 1 (fix votes constraint).

### Issue: Vote counts seem wrong
**Solution:** Run Migration 2 (cleanup legacy votes).

### Issue: Optimistic counts still double-counting
**Solution:**
- Make sure you pulled the latest code from the branch
- Rebuild the app: `./gradlew clean build`
- Clear app data and restart

---

## 📝 Summary

**What you need to do:**
1. Deploy edge function: `supabase functions deploy cast_vote`
2. Apply migrations: `supabase db push` (or run SQL manually)
3. Test voting in the app
4. Verify no errors in logs

**Expected result after deployment:**
- ✅ Votes record with user_id
- ✅ Vote counts update instantly on UI
- ✅ No flickering or double-counting
- ✅ Anonymous users see auth dialog
- ✅ Duplicate votes prevented

---

**Need help?** Check the Supabase CLI documentation:
https://supabase.com/docs/reference/cli/introduction
