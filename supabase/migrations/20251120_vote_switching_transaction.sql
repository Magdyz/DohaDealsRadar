-- ========================================
-- VOTING SYSTEM ENHANCEMENT: Vote Switching Transaction Function
-- ========================================
-- Created: 2025-11-20
-- Purpose: Enable users to switch votes (hot ↔ cold) and un-vote
--
-- Features:
-- 1. NEW VOTE: User has not voted → Insert vote + increment count
-- 2. SWITCH VOTE: User voted hot, now votes cold → Update vote_type + adjust counts
-- 3. UN-VOTE: User clicks same vote type again → Delete vote + decrement count
-- 4. ATOMIC: All operations in single transaction (vote + counts)
-- 5. SAFE: Row-level locks prevent race conditions
-- 6. BACKWARD COMPATIBLE: Supports both user_id and device_id voting
--
-- Returns:
-- - action: 'added' | 'switched' | 'removed'
-- - hot_count: Updated hot vote count
-- - cold_count: Updated cold vote count
-- ========================================

-- ========================================
-- MAIN TRANSACTION FUNCTION
-- ========================================

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
  SELECT hot_count, cold_count INTO v_hot_count, v_cold_count
  FROM deals
  WHERE id = p_deal_id
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
      IF p_vote_type = 'hot' THEN
        UPDATE deals
        SET hot_count = GREATEST(hot_count - 1, 0)  -- Prevent negative counts
        WHERE id = p_deal_id
        RETURNING hot_count, cold_count INTO v_hot_count, v_cold_count;
      ELSE
        UPDATE deals
        SET cold_count = GREATEST(cold_count - 1, 0)  -- Prevent negative counts
        WHERE id = p_deal_id
        RETURNING hot_count, cold_count INTO v_hot_count, v_cold_count;
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
      IF p_vote_type = 'hot' THEN
        -- Switching from cold to hot
        UPDATE deals
        SET
          hot_count = hot_count + 1,
          cold_count = GREATEST(cold_count - 1, 0)  -- Prevent negative
        WHERE id = p_deal_id
        RETURNING hot_count, cold_count INTO v_hot_count, v_cold_count;
      ELSE
        -- Switching from hot to cold
        UPDATE deals
        SET
          hot_count = GREATEST(hot_count - 1, 0),  -- Prevent negative
          cold_count = cold_count + 1
        WHERE id = p_deal_id
        RETURNING hot_count, cold_count INTO v_hot_count, v_cold_count;
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
    IF p_vote_type = 'hot' THEN
      UPDATE deals
      SET hot_count = hot_count + 1
      WHERE id = p_deal_id
      RETURNING hot_count, cold_count INTO v_hot_count, v_cold_count;
    ELSE
      UPDATE deals
      SET cold_count = cold_count + 1
      WHERE id = p_deal_id
      RETURNING hot_count, cold_count INTO v_hot_count, v_cold_count;
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
-- FUNCTION METADATA
-- ========================================

COMMENT ON FUNCTION cast_vote_atomic IS
'Atomic vote operation supporting NEW, SWITCH, and REMOVE actions.
- NEW: User has not voted → Insert + increment count
- SWITCH: User changes vote type → Update vote + adjust counts
- REMOVE: User clicks same type → Delete + decrement count
- Uses row-level locks to prevent race conditions
- Returns: (action TEXT, hot_count INT, cold_count INT)
- Supports both user_id (authenticated) and device_id (legacy) voting';

-- ========================================
-- GRANT PERMISSIONS
-- ========================================

-- Grant execute permission to authenticated users
GRANT EXECUTE ON FUNCTION cast_vote_atomic TO authenticated;

-- Grant execute permission to service role (for edge functions)
GRANT EXECUTE ON FUNCTION cast_vote_atomic TO service_role;

-- ========================================
-- TESTING EXAMPLES (for manual verification)
-- ========================================

-- Test 1: New vote
-- SELECT * FROM cast_vote_atomic(
--   '00000000-0000-0000-0000-000000000001'::UUID,  -- deal_id
--   'hot',                                          -- vote_type
--   '11111111-1111-1111-1111-111111111111'::UUID,  -- user_id
--   NULL                                            -- device_id
-- );
-- Expected: action='added', hot_count +1

-- Test 2: Switch vote (hot → cold)
-- SELECT * FROM cast_vote_atomic(
--   '00000000-0000-0000-0000-000000000001'::UUID,
--   'cold',
--   '11111111-1111-1111-1111-111111111111'::UUID,
--   NULL
-- );
-- Expected: action='switched', hot_count -1, cold_count +1

-- Test 3: Un-vote (cold → cold)
-- SELECT * FROM cast_vote_atomic(
--   '00000000-0000-0000-0000-000000000001'::UUID,
--   'cold',
--   '11111111-1111-1111-1111-111111111111'::UUID,
--   NULL
-- );
-- Expected: action='removed', cold_count -1

-- Test 4: Device-based vote (backward compatibility)
-- SELECT * FROM cast_vote_atomic(
--   '00000000-0000-0000-0000-000000000001'::UUID,
--   'hot',
--   NULL,                    -- user_id NULL
--   'device-abc-123'         -- device_id
-- );
-- Expected: action='added', hot_count +1

-- ========================================
-- MIGRATION COMPLETE
-- ========================================
