-- ========================================
-- FIX: votes_identifier_check Constraint
-- ========================================
-- Created: 2025-11-20
-- Purpose: Fix the votes_identifier_check constraint to allow user_id
--          while keeping device_id optional for analytics
--
-- Problem:
-- Current constraint requires ONLY ONE identifier (user_id OR device_id)
-- This causes votes to fail when both are provided
--
-- Solution:
-- - Drop the restrictive CHECK constraint
-- - Add new constraint: require user_id, allow optional device_id
-- - This supports authenticated voting with device tracking for analytics
-- ========================================

-- ========================================
-- STEP 1: Drop existing restrictive constraint
-- ========================================

ALTER TABLE votes
DROP CONSTRAINT IF EXISTS votes_identifier_check;

COMMENT ON TABLE votes IS 'Fixed: Removed restrictive identifier check constraint';

-- ========================================
-- STEP 2: Add new constraint - require user_id (device_id optional)
-- ========================================

-- For new votes, user_id is required (device_id is optional for analytics)
ALTER TABLE votes
ADD CONSTRAINT votes_user_id_required
CHECK (
  (user_id IS NOT NULL) OR
  (user_id IS NULL AND device_id IS NOT NULL AND created_at < '2025-11-20'::timestamp)
);

COMMENT ON CONSTRAINT votes_user_id_required ON votes IS
'Requires user_id for new votes (post 2025-11-20). Legacy device-only votes (before 2025-11-20) are allowed for backward compatibility.';

-- ========================================
-- STEP 3: Ensure data integrity
-- ========================================

-- Update any existing votes that have both user_id and device_id
-- (This should not happen but ensures clean data)
-- No action needed - both can coexist for analytics

-- ========================================
-- VERIFICATION QUERY
-- ========================================

-- Run this after migration to verify:
-- SELECT
--   COUNT(*) as total_votes,
--   COUNT(user_id) as user_id_votes,
--   COUNT(device_id) as device_id_votes,
--   COUNT(*) FILTER (WHERE user_id IS NOT NULL AND device_id IS NOT NULL) as both_present
-- FROM votes;
