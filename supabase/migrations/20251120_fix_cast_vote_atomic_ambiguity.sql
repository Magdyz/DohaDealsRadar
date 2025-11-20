-- ========================================
-- FIX: cast_vote_atomic ambiguous column reference
-- ========================================
-- Created: 2025-11-20
-- Purpose: Fix "column reference hot_count is ambiguous" error
--
-- Issue:
-- The original cast_vote_atomic function had unqualified column
-- references (hot_count, cold_count) which caused ambiguity errors
-- in some PostgreSQL/PostgREST execution contexts.
--
-- Solution:
-- Explicitly qualify all column references with table name (deals.hot_count)
-- This is a safe, non-breaking change that improves query clarity.
-- ========================================

-- This is a CREATE OR REPLACE, so it safely updates the existing function
CREATE OR REPLACE FUNCTION cast_vote_atomic(
  p_deal_id UUID,
  p_vote_type TEXT,
  p_user_id UUID DEFAULT NULL,
  p_device_id TEXT DEFAULT NULL
)
RETURNS TABLE(
  action TEXT,
  hot_count INTEGER,
  cold_count INTEGER
)
LANGUAGE plpgsql
AS $$
DECLARE
  v_existing_vote RECORD;
  v_action TEXT;
  v_hot_count INTEGER;
  v_cold_count INTEGER;
BEGIN
  -- ========================================
  -- VALIDATION
  -- ========================================

  -- Check vote_type is valid
  IF p_vote_type NOT IN ('hot', 'cold') THEN
    RAISE EXCEPTION 'Invalid vote_type: %. Must be "hot" or "cold"', p_vote_type;
  END IF;

  -- Check deal exists and lock it (prevent concurrent modifications)
  -- ✅ FIXED: Qualify all column references with table name
  SELECT deals.hot_count, deals.cold_count INTO v_hot_count, v_cold_count
  FROM deals
  WHERE deals.id = p_deal_id
  FOR UPDATE;  -- Row-level lock on the deal

  IF NOT FOUND THEN
    RAISE EXCEPTION 'Deal not found: %', p_deal_id;
  END IF;

  -- ========================================
  -- CHECK FOR EXISTING VOTE
  -- ========================================

  v_existing_vote := NULL;

  -- PRIORITY 1: Check by user_id (authenticated votes)
  IF p_user_id IS NOT NULL THEN
    SELECT * INTO v_existing_vote
    FROM votes
    WHERE user_id = p_user_id AND deal_id = p_deal_id
    FOR UPDATE;  -- Lock the vote row if exists

  -- PRIORITY 2: Fallback to device_id (legacy/anonymous votes)
  ELSIF p_device_id IS NOT NULL THEN
    SELECT * INTO v_existing_vote
    FROM votes
    WHERE device_id = p_device_id AND deal_id = p_deal_id AND user_id IS NULL
    FOR UPDATE;  -- Lock the vote row if exists

  ELSE
    -- Neither user_id nor device_id provided
    RAISE EXCEPTION 'Either user_id or device_id must be provided';
  END IF;

  -- ========================================
  -- CASE 1: EXISTING VOTE FOUND
  -- ========================================

  IF v_existing_vote IS NOT NULL THEN

    -- ----------------------------------------
    -- CASE 1A: SAME VOTE TYPE = UN-VOTE (Remove)
    -- ----------------------------------------
    IF v_existing_vote.vote_type = p_vote_type THEN

      -- Delete the vote
      DELETE FROM votes WHERE id = v_existing_vote.id;

      -- Decrement the count
      -- ✅ FIXED: Qualify all column references
      IF p_vote_type = 'hot' THEN
        UPDATE deals
        SET hot_count = GREATEST(deals.hot_count - 1, 0)  -- Prevent negative counts
        WHERE deals.id = p_deal_id
        RETURNING deals.hot_count, deals.cold_count INTO v_hot_count, v_cold_count;
      ELSE
        UPDATE deals
        SET cold_count = GREATEST(deals.cold_count - 1, 0)  -- Prevent negative counts
        WHERE deals.id = p_deal_id
        RETURNING deals.hot_count, deals.cold_count INTO v_hot_count, v_cold_count;
      END IF;

      v_action := 'removed';

      RAISE NOTICE 'Vote removed: user_id=%, deal_id=%, vote_type=%', p_user_id, p_deal_id, p_vote_type;

    -- ----------------------------------------
    -- CASE 1B: DIFFERENT VOTE TYPE = SWITCH
    -- ----------------------------------------
    ELSE

      -- Update the vote_type and timestamp
      UPDATE votes
      SET
        vote_type = p_vote_type,
        created_at = NOW()  -- Update timestamp to reflect switch time
      WHERE id = v_existing_vote.id;

      -- Adjust counts: -1 from old type, +1 to new type
      -- ✅ FIXED: Qualify all column references
      IF p_vote_type = 'hot' THEN
        -- Switching from cold to hot
        UPDATE deals
        SET
          hot_count = deals.hot_count + 1,
          cold_count = GREATEST(deals.cold_count - 1, 0)  -- Prevent negative
        WHERE deals.id = p_deal_id
        RETURNING deals.hot_count, deals.cold_count INTO v_hot_count, v_cold_count;
      ELSE
        -- Switching from hot to cold
        UPDATE deals
        SET
          hot_count = GREATEST(deals.hot_count - 1, 0),  -- Prevent negative
          cold_count = deals.cold_count + 1
        WHERE deals.id = p_deal_id
        RETURNING deals.hot_count, deals.cold_count INTO v_hot_count, v_cold_count;
      END IF;

      v_action := 'switched';

      RAISE NOTICE 'Vote switched: user_id=%, deal_id=%, % → %', p_user_id, p_deal_id, v_existing_vote.vote_type, p_vote_type;

    END IF;

  -- ========================================
  -- CASE 2: NO EXISTING VOTE = NEW VOTE
  -- ========================================

  ELSE

    -- Insert new vote
    INSERT INTO votes (deal_id, vote_type, user_id, device_id)
    VALUES (p_deal_id, p_vote_type, p_user_id, p_device_id);

    -- Increment the count
    -- ✅ FIXED: Qualify all column references
    IF p_vote_type = 'hot' THEN
      UPDATE deals
      SET hot_count = deals.hot_count + 1
      WHERE deals.id = p_deal_id
      RETURNING deals.hot_count, deals.cold_count INTO v_hot_count, v_cold_count;
    ELSE
      UPDATE deals
      SET cold_count = deals.cold_count + 1
      WHERE deals.id = p_deal_id
      RETURNING deals.hot_count, deals.cold_count INTO v_hot_count, v_cold_count;
    END IF;

    v_action := 'added';

    RAISE NOTICE 'Vote added: user_id=%, deal_id=%, vote_type=%', p_user_id, p_deal_id, p_vote_type;

  END IF;

  -- ========================================
  -- RETURN RESULT
  -- ========================================

  RETURN QUERY SELECT v_action, v_hot_count, v_cold_count;

EXCEPTION
  WHEN OTHERS THEN
    -- Log error and re-raise
    RAISE WARNING 'cast_vote_atomic failed: % (SQLSTATE: %)', SQLERRM, SQLSTATE;
    RAISE;
END;
$$;

-- ========================================
-- VERIFICATION
-- ========================================

COMMENT ON FUNCTION cast_vote_atomic IS
'Atomic vote operation supporting NEW, SWITCH, and REMOVE actions.
FIXED: All column references explicitly qualified to prevent ambiguity.
- NEW: User has not voted → Insert + increment count
- SWITCH: User changes vote type → Update vote + adjust counts
- REMOVE: User clicks same type → Delete + decrement count
- Uses row-level locks to prevent race conditions
- Returns: (action TEXT, hot_count INT, cold_count INT)
- Supports both user_id (authenticated) and device_id (legacy) voting';

-- ========================================
-- MIGRATION COMPLETE
-- ========================================
-- This migration fixes the "column reference hot_count is ambiguous" error
-- by explicitly qualifying all column references with the table name.
-- This is a safe, backward-compatible change.
