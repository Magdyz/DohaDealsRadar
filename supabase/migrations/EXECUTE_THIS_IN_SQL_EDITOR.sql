-- ========================================
-- 📋 COPY AND PASTE THIS ENTIRE SCRIPT INTO SUPABASE SQL EDITOR
-- ========================================
-- Migration: Add user_id to votes table
-- Date: 2025-11-19
-- Safe to run: YES (backwards compatible)
-- ========================================

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

-- ========================================
-- ✅ MIGRATION COMPLETE
-- ========================================
-- Run the verification query below to confirm:

SELECT
    COUNT(*) as total_votes,
    COUNT(device_id) as device_based_votes,
    COUNT(user_id) as user_based_votes
FROM votes;

-- Expected result:
-- total_votes = all your existing votes
-- device_based_votes = all your existing votes
-- user_based_votes = 0 (will increase as users vote after app update)
