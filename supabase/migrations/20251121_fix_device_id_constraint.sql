-- ========================================
-- FIX: Remove Legacy Device-Only Unique Constraint
-- ========================================
-- Created: 2025-11-21
-- Purpose: Fix voting system for authenticated-only voting
--
-- Policy: ONLY authenticated users can vote (no anonymous votes)
--
-- Problem:
-- - Old constraint `votes_deal_id_device_id_key` on (deal_id, device_id)
-- - Causes conflicts when authenticated users vote with device_id present
-- - Error: "duplicate key value violates unique constraint votes_deal_id_device_id_key"
--
-- Solution:
-- 1. Drop the legacy device_id constraint
-- 2. Clean up all device-only votes (anonymous votes not allowed)
-- 3. Rely on idx_votes_user_deal for uniqueness: (user_id, deal_id)
-- ========================================

-- ========================================
-- STEP 1: Drop Legacy Constraint
-- ========================================

ALTER TABLE votes
DROP CONSTRAINT IF EXISTS votes_deal_id_device_id_key;

COMMENT ON TABLE votes IS
'Authenticated-only voting: removed legacy device_id constraint';

-- ========================================
-- STEP 2: Drop Legacy Device Index (if exists)
-- ========================================

-- Drop the old device_id index since it's no longer needed
DROP INDEX IF EXISTS idx_votes_device_deal;

COMMENT ON COLUMN votes.device_id IS
'Device ID for analytics only (optional). Voting requires user_id.';

-- ========================================
-- STEP 3: Clean Up All Anonymous Votes
-- ========================================

-- Delete all votes without user_id (anonymous votes)
-- Policy: only authenticated users can vote
DO $$
DECLARE
    deleted_count INTEGER;
BEGIN
    DELETE FROM votes
    WHERE user_id IS NULL;

    GET DIAGNOSTICS deleted_count = ROW_COUNT;

    RAISE NOTICE '========================================';
    RAISE NOTICE 'ANONYMOUS VOTE CLEANUP';
    RAISE NOTICE '========================================';
    RAISE NOTICE 'Deleted % anonymous votes (user_id IS NULL)', deleted_count;
    RAISE NOTICE 'Policy: Only authenticated users can vote';
    RAISE NOTICE '========================================';
END $$;

-- ========================================
-- STEP 4: Update Vote Counts After Cleanup
-- ========================================

DO $$
BEGIN
    -- Recalculate hot_count for all deals
    UPDATE deals
    SET hot_count = (
        SELECT COUNT(*)
        FROM votes
        WHERE votes.deal_id = deals.id
        AND votes.vote_type = 'hot'
    );

    -- Recalculate cold_count for all deals
    UPDATE deals
    SET cold_count = (
        SELECT COUNT(*)
        FROM votes
        WHERE votes.deal_id = deals.id
        AND votes.vote_type = 'cold'
    );

    RAISE NOTICE '✅ Recalculated vote counts for all deals';
END $$;

-- ========================================
-- STEP 5: Verify Current State
-- ========================================

DO $$
DECLARE
    constraint_exists BOOLEAN;
    remaining_anonymous INTEGER;
BEGIN
    -- Check if legacy constraint is gone
    SELECT EXISTS (
        SELECT 1
        FROM pg_constraint
        WHERE conname = 'votes_deal_id_device_id_key'
    ) INTO constraint_exists;

    -- Check for remaining anonymous votes
    SELECT COUNT(*) INTO remaining_anonymous
    FROM votes
    WHERE user_id IS NULL;

    RAISE NOTICE '========================================';
    RAISE NOTICE 'VERIFICATION';
    RAISE NOTICE '========================================';

    IF constraint_exists THEN
        RAISE WARNING '⚠️ Legacy constraint still exists!';
    ELSE
        RAISE NOTICE '✅ Legacy constraint removed';
    END IF;

    IF remaining_anonymous > 0 THEN
        RAISE WARNING '⚠️ Still have % anonymous votes!', remaining_anonymous;
    ELSE
        RAISE NOTICE '✅ All anonymous votes cleaned up';
    END IF;

    RAISE NOTICE '========================================';
END $$;

-- ========================================
-- SUMMARY OF CONSTRAINTS
-- ========================================

-- After this migration, votes table has:
-- 1. PRIMARY KEY: id
-- 2. FOREIGN KEY: user_id → users(id)
-- 3. UNIQUE INDEX: idx_votes_user_deal on (user_id, deal_id) WHERE user_id IS NOT NULL
-- 4. INDEXES: deal_id, user_id, created_at
-- 5. No device_id constraints (device_id is optional for analytics only)
