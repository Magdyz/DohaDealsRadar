-- ========================================
-- VOTING SYSTEM MIGRATION: Add user_id to votes table
-- ========================================
-- Created: 2025-11-19
-- Purpose: Migrate from device-based voting to user-authenticated voting
--
-- Changes:
-- 1. Add user_id column to votes table
-- 2. Add foreign key constraint to users table
-- 3. Add unique constraint for one vote per user per deal
-- 4. Create indexes for performance
-- 5. Keep device_id for backward compatibility and analytics
--
-- Backward Compatibility:
-- - user_id is NULLABLE to support legacy votes
-- - device_id remains in table for existing votes
-- - New votes MUST have user_id (enforced by application layer)
-- ========================================

-- ========================================
-- STEP 1: Add user_id column
-- ========================================

ALTER TABLE votes
ADD COLUMN IF NOT EXISTS user_id UUID;

COMMENT ON COLUMN votes.user_id IS 'User who cast the vote (NULL for legacy device-based votes)';

-- ========================================
-- STEP 2: Add foreign key constraint
-- ========================================

-- Ensure vote belongs to a valid user
ALTER TABLE votes
ADD CONSTRAINT fk_votes_user_id
FOREIGN KEY (user_id)
REFERENCES users(id)
ON DELETE CASCADE;

COMMENT ON CONSTRAINT fk_votes_user_id ON votes IS 'Ensures vote belongs to valid user, cascade delete when user deleted';

-- ========================================
-- STEP 3: Add unique constraint
-- ========================================

-- Prevent duplicate votes: one vote per user per deal
CREATE UNIQUE INDEX IF NOT EXISTS idx_votes_user_deal
ON votes(user_id, deal_id)
WHERE user_id IS NOT NULL;

COMMENT ON INDEX idx_votes_user_deal IS 'Ensures one vote per user per deal (NULL user_id excluded for legacy votes)';

-- ========================================
-- STEP 4: Create performance indexes
-- ========================================

-- Index for querying votes by user (for user profile/history)
CREATE INDEX IF NOT EXISTS idx_votes_user_id
ON votes(user_id)
WHERE user_id IS NOT NULL;

-- Index for querying votes by deal (for vote count aggregation)
CREATE INDEX IF NOT EXISTS idx_votes_deal_id
ON votes(deal_id);

-- Keep existing device_id index for legacy support and analytics
CREATE INDEX IF NOT EXISTS idx_votes_device_deal
ON votes(device_id, deal_id)
WHERE device_id IS NOT NULL;

-- Index for time-based analytics
CREATE INDEX IF NOT EXISTS idx_votes_created_at
ON votes(created_at DESC);

-- ========================================
-- STEP 5: Add helpful comments
-- ========================================

COMMENT ON TABLE votes IS 'User votes on deals. Supports both user-authenticated (user_id) and legacy device-based (device_id) votes.';
COMMENT ON COLUMN votes.device_id IS 'Device ID for legacy votes and analytics (optional for new votes)';
COMMENT ON COLUMN votes.vote_type IS 'Type of vote: hot or cold';
COMMENT ON COLUMN votes.deal_id IS 'Deal being voted on';

-- ========================================
-- VERIFICATION QUERY
-- ========================================

-- Run this after migration to verify:
-- SELECT column_name, data_type, is_nullable, column_default
-- FROM information_schema.columns
-- WHERE table_name = 'votes'
-- ORDER BY ordinal_position;

-- Expected columns:
-- id          | uuid    | NO  | gen_random_uuid()
-- deal_id     | uuid    | NO  |
-- device_id   | text    | YES |
-- vote_type   | text    | NO  |
-- created_at  | timestamp | NO | now()
-- user_id     | uuid    | YES | NULL
