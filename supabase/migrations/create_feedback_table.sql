-- ========================================
-- Feedback Table for DohaDealsRadar
-- Stores user feedback and suggestions
-- CREATED: 2025-11-22
-- ========================================

-- Create feedback table
CREATE TABLE IF NOT EXISTS feedback (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    device_id TEXT NOT NULL,
    user_id UUID,  -- Optional: Link to users table if user is logged in
    feedback_text TEXT NOT NULL,
    email TEXT,  -- Optional: User email if they want a response
    status TEXT DEFAULT 'pending' CHECK (status IN ('pending', 'reviewed', 'resolved', 'archived')),
    created_at TIMESTAMPTZ DEFAULT NOW(),
    reviewed_at TIMESTAMPTZ,
    reviewed_by UUID,  -- Admin/moderator who reviewed
    notes TEXT  -- Admin notes about the feedback
);

-- Create index for faster queries
CREATE INDEX idx_feedback_device_id ON feedback(device_id);
CREATE INDEX idx_feedback_status ON feedback(status);
CREATE INDEX idx_feedback_created_at ON feedback(created_at DESC);
CREATE INDEX idx_feedback_user_id ON feedback(user_id) WHERE user_id IS NOT NULL;

-- Enable Row Level Security
ALTER TABLE feedback ENABLE ROW LEVEL SECURITY;

-- Policy: Users can insert their own feedback
CREATE POLICY "Users can submit feedback"
ON feedback
FOR INSERT
WITH CHECK (true);  -- Anyone can submit feedback

-- Policy: Users can view their own feedback
CREATE POLICY "Users can view own feedback"
ON feedback
FOR SELECT
USING (device_id = current_setting('app.device_id', true));

-- Policy: Admins and moderators can view all feedback
CREATE POLICY "Admins and moderators can view all feedback"
ON feedback
FOR SELECT
USING (
    EXISTS (
        SELECT 1 FROM users
        WHERE users.device_id = current_setting('app.device_id', true)
        AND users.role IN ('admin', 'moderator')
    )
);

-- Policy: Admins and moderators can update feedback (review, add notes)
CREATE POLICY "Admins and moderators can update feedback"
ON feedback
FOR UPDATE
USING (
    EXISTS (
        SELECT 1 FROM users
        WHERE users.device_id = current_setting('app.device_id', true)
        AND users.role IN ('admin', 'moderator')
    )
);

-- Grant permissions
GRANT SELECT, INSERT ON feedback TO authenticated;
GRANT SELECT, INSERT ON feedback TO anon;

COMMENT ON TABLE feedback IS 'User feedback and suggestions for DohaDealsRadar';
COMMENT ON COLUMN feedback.device_id IS 'Device ID of the user submitting feedback';
COMMENT ON COLUMN feedback.user_id IS 'Optional user ID if user is authenticated';
COMMENT ON COLUMN feedback.feedback_text IS 'The actual feedback content (max 500 chars enforced by app)';
COMMENT ON COLUMN feedback.status IS 'Status of feedback: pending, reviewed, resolved, or archived';
COMMENT ON COLUMN feedback.reviewed_by IS 'Admin/moderator who reviewed the feedback';
COMMENT ON COLUMN feedback.notes IS 'Internal notes from admins/moderators';
