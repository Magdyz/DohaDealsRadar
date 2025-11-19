-- ========================================
-- MIGRATION: Add user_id to votes table
-- Created: 2025-11-19
-- Purpose: Transition from device-based to user-authenticated voting
-- ========================================

-- STEP 1: Add user_id column (nullable for backwards compatibility)
-- This allows old votes with device_id to remain valid
ALTER TABLE votes
ADD COLUMN user_id UUID REFERENCES users(id) ON DELETE CASCADE;

-- Add index for performance on user_id lookups
CREATE INDEX idx_votes_user_id ON votes(user_id);

-- STEP 2: Make device_id nullable (for backwards compatibility)
-- Old votes have device_id, new votes have user_id
ALTER TABLE votes
ALTER COLUMN device_id DROP NOT NULL;

-- STEP 3: Create unique constraint for user-based votes
-- Prevents duplicate votes from same user on same deal
-- Only applies to new votes (where user_id IS NOT NULL)
CREATE UNIQUE INDEX idx_votes_user_deal_unique
ON votes(user_id, deal_id)
WHERE user_id IS NOT NULL;

-- STEP 4: Add check constraint to ensure either device_id OR user_id exists
-- Every vote must have at least one identifier
ALTER TABLE votes
ADD CONSTRAINT votes_identifier_check
CHECK (
    (device_id IS NOT NULL AND user_id IS NULL) OR  -- Old votes (device-based)
    (device_id IS NULL AND user_id IS NOT NULL)      -- New votes (user-based)
);

-- ========================================
-- VERIFICATION QUERIES (run after migration)
-- ========================================

-- Check existing votes structure
-- SELECT
--     COUNT(*) as total_votes,
--     COUNT(device_id) as device_based_votes,
--     COUNT(user_id) as user_based_votes
-- FROM votes;

-- Verify constraints
-- SELECT constraint_name, constraint_type
-- FROM information_schema.table_constraints
-- WHERE table_name = 'votes';

-- ========================================
-- ROLLBACK PLAN (if needed)
-- ========================================

-- CAUTION: Only run if you need to rollback this migration
-- DROP INDEX IF EXISTS idx_votes_user_deal_unique;
-- DROP INDEX IF EXISTS idx_votes_user_id;
-- ALTER TABLE votes DROP CONSTRAINT IF EXISTS votes_identifier_check;
-- ALTER TABLE votes DROP COLUMN IF EXISTS user_id;
-- ALTER TABLE votes ALTER COLUMN device_id SET NOT NULL;

-- ========================================
-- MIGRATION NOTES
-- ========================================
-- ✅ Backwards compatible: Old votes with device_id remain valid
-- ✅ Forward compatible: New votes require user_id
-- ✅ Data integrity: Unique constraint prevents duplicate votes per user
-- ✅ No data loss: All existing votes preserved
-- ✅ Performance: Indexes added for fast lookups
-- ✅ Referential integrity: Foreign key to users table with CASCADE delete
