-- ========================================
-- CLEANUP: Remove votes without user_id
-- ========================================
-- Created: 2025-11-20
-- Purpose: Clean up legacy votes that don't have user_id
--
-- REASON:
-- - New voting system requires user_id for all votes
-- - Old votes with only device_id may be causing counting issues
-- - Fresh start ensures vote counts are accurate
--
-- IMPACT:
-- - Removes all votes where user_id IS NULL
-- - Decrements hot_count and cold_count on affected deals
-- - Cannot be undone (backup recommended before running)
--
-- SAFETY:
-- - Only affects votes table, not deals table
-- - Vote counts will be recalculated based on remaining votes
-- ========================================

-- ========================================
-- STEP 1: Log votes to be deleted (for audit)
-- ========================================

DO $$
DECLARE
    votes_to_delete INTEGER;
    hot_votes_to_delete INTEGER;
    cold_votes_to_delete INTEGER;
BEGIN
    SELECT COUNT(*) INTO votes_to_delete
    FROM votes
    WHERE user_id IS NULL;

    SELECT COUNT(*) INTO hot_votes_to_delete
    FROM votes
    WHERE user_id IS NULL AND vote_type = 'hot';

    SELECT COUNT(*) INTO cold_votes_to_delete
    FROM votes
    WHERE user_id IS NULL AND vote_type = 'cold';

    RAISE NOTICE '========================================';
    RAISE NOTICE 'VOTES CLEANUP AUDIT';
    RAISE NOTICE '========================================';
    RAISE NOTICE 'Total votes without user_id: %', votes_to_delete;
    RAISE NOTICE 'Hot votes without user_id: %', hot_votes_to_delete;
    RAISE NOTICE 'Cold votes without user_id: %', cold_votes_to_delete;
    RAISE NOTICE '========================================';
END $$;

-- ========================================
-- STEP 2: Update deal counts BEFORE deletion
-- ========================================

-- Decrement hot_count for each deal with deleted hot votes
UPDATE deals
SET hot_count = GREATEST(0, hot_count - (
    SELECT COUNT(*)
    FROM votes
    WHERE votes.deal_id = deals.id
    AND votes.user_id IS NULL
    AND votes.vote_type = 'hot'
))
WHERE id IN (
    SELECT DISTINCT deal_id
    FROM votes
    WHERE user_id IS NULL AND vote_type = 'hot'
);

-- Decrement cold_count for each deal with deleted cold votes
UPDATE deals
SET cold_count = GREATEST(0, cold_count - (
    SELECT COUNT(*)
    FROM votes
    WHERE votes.deal_id = deals.id
    AND votes.user_id IS NULL
    AND votes.vote_type = 'cold'
))
WHERE id IN (
    SELECT DISTINCT deal_id
    FROM votes
    WHERE user_id IS NULL AND vote_type = 'cold'
);

-- ========================================
-- STEP 3: Delete votes without user_id
-- ========================================

DELETE FROM votes
WHERE user_id IS NULL;

-- ========================================
-- STEP 4: Verify cleanup
-- ========================================

DO $$
DECLARE
    remaining_votes INTEGER;
BEGIN
    SELECT COUNT(*) INTO remaining_votes
    FROM votes
    WHERE user_id IS NULL;

    RAISE NOTICE '========================================';
    RAISE NOTICE 'CLEANUP VERIFICATION';
    RAISE NOTICE '========================================';
    RAISE NOTICE 'Remaining votes without user_id: %', remaining_votes;

    IF remaining_votes = 0 THEN
        RAISE NOTICE '✅ Cleanup successful!';
    ELSE
        RAISE NOTICE '⚠️ Warning: Some votes remain without user_id';
    END IF;
    RAISE NOTICE '========================================';
END $$;

-- ========================================
-- OPTIONAL: Recalculate all vote counts (safety check)
-- Run this if you want to ensure perfect accuracy
-- ========================================

/*
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
*/
