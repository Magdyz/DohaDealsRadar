-- ========================================
-- FIX VOTES IDENTIFIER CHECK CONSTRAINT
-- ========================================
-- Created: 2025-11-20
-- Purpose: Update the votes_identifier_check constraint to work with new user-based voting
--
-- Issue: The existing constraint may be too restrictive or incompatible with user_id requirement
-- Solution: Drop old constraint and recreate with proper logic
-- ========================================

-- ========================================
-- STEP 1: Drop existing constraint (if exists)
-- ========================================

ALTER TABLE votes DROP CONSTRAINT IF EXISTS votes_identifier_check;

-- ========================================
-- STEP 2: Add new constraint
-- ========================================

-- New constraint: At least one identifier (user_id OR device_id) must be present
-- This allows:
-- - New votes with user_id (device_id optional)
-- - Legacy votes with device_id only
ALTER TABLE votes
ADD CONSTRAINT votes_identifier_check
CHECK (
  user_id IS NOT NULL OR device_id IS NOT NULL
);

COMMENT ON CONSTRAINT votes_identifier_check ON votes IS 'Ensures at least one identifier (user_id or device_id) is present for vote tracking';

-- ========================================
-- VERIFICATION QUERY
-- ========================================

-- Verify constraint was created:
-- SELECT conname, contype, pg_get_constraintdef(oid)
-- FROM pg_constraint
-- WHERE conrelid = 'votes'::regclass
-- AND conname = 'votes_identifier_check';
