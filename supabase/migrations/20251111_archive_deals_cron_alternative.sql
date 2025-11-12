-- ========================================
-- SIMPLER APPROACH: Direct database function
-- ========================================
-- This approach doesn't require HTTP calls or the net extension
-- It's more reliable and efficient

-- Enable pg_cron extension for scheduled jobs
CREATE EXTENSION IF NOT EXISTS pg_cron WITH SCHEMA extensions;

-- Create a database function to archive expired deals
CREATE OR REPLACE FUNCTION archive_expired_deals()
RETURNS TABLE (
  archived_count integer,
  archived_deal_ids uuid[]
)
LANGUAGE plpgsql
SECURITY DEFINER
AS $BODY$
DECLARE
  v_archived_count integer;
  v_archived_ids uuid[];
BEGIN
  -- Archive all deals where expires_at is in the past and not already archived
  WITH archived AS (
    UPDATE deals
    SET is_archived = true
    WHERE expires_at < NOW()
      AND is_archived = false
      AND expires_at IS NOT NULL
    RETURNING id
  )
  SELECT
    COUNT(*)::integer,
    ARRAY_AGG(id)
  INTO v_archived_count, v_archived_ids
  FROM archived;

  -- Log the result
  RAISE NOTICE 'Archive job completed: % deal(s) archived', COALESCE(v_archived_count, 0);

  -- Return results
  RETURN QUERY SELECT v_archived_count, v_archived_ids;
END;
$BODY$;

-- Remove existing cron job if it exists
SELECT cron.unschedule('archive-expired-deals-daily');

-- Schedule the archive job to run daily at midnight UTC
SELECT
  cron.schedule(
    'archive-expired-deals-daily',
    '0 0 * * *', -- Every day at midnight UTC
    $CRON$
    SELECT archive_expired_deals();
    $CRON$
  );

-- Verify the cron job was created
SELECT * FROM cron.job WHERE jobname = 'archive-expired-deals-daily';

-- Grant execute permission (adjust role as needed)
GRANT EXECUTE ON FUNCTION archive_expired_deals() TO postgres, service_role;
