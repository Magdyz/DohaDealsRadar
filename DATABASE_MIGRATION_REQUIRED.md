## ⚠️ CRITICAL: Database Migration Required!

### The HTTP 500 errors you're seeing are because the database migration hasn't been executed yet.

The backend `cast_vote` function is now expecting a `user_id` column in the `votes` table, but it doesn't exist yet.

---

## 🔴 IMMEDIATE ACTION REQUIRED

### Step 1: Run Database Migration

**Open Supabase SQL Editor** and execute this SQL:

```sql
-- Add user_id column
ALTER TABLE votes
ADD COLUMN user_id UUID REFERENCES users(id) ON DELETE CASCADE;

-- Add index for performance
CREATE INDEX idx_votes_user_id ON votes(user_id);

-- Make device_id nullable (backwards compatibility)
ALTER TABLE votes
ALTER COLUMN device_id DROP NOT NULL;

-- Prevent duplicate votes per user per deal
CREATE UNIQUE INDEX idx_votes_user_deal_unique
ON votes(user_id, deal_id)
WHERE user_id IS NOT NULL;

-- Ensure every vote has either device_id OR user_id
ALTER TABLE votes
ADD CONSTRAINT votes_identifier_check
CHECK (
    (device_id IS NOT NULL AND user_id IS NULL) OR
    (device_id IS NULL AND user_id IS NOT NULL)
);
```

**Or simply copy the file:**
```bash
supabase/migrations/EXECUTE_THIS_IN_SQL_EDITOR.sql
```

---

### Step 2: Verify Migration

Run this query to confirm:
```sql
SELECT
    COUNT(*) as total_votes,
    COUNT(device_id) as device_based_votes,
    COUNT(user_id) as user_based_votes
FROM votes;
```

Expected result:
- `total_votes` = your existing votes
- `device_based_votes` = all existing votes
- `user_based_votes` = 0

---

### Step 3: Test Voting Again

After the migration, voting should work without HTTP 500 errors.

---

## 🐛 Other Issues Being Fixed

While the HTTP 500 is the main blocker, I'm also fixing:
1. ✅ Optimistic updates in DetailsScreen (currently missing)
2. ✅ State synchronization between DealCard and DetailsScreen
3. ✅ Proper vote count display across all screens

These fixes are being implemented now...
