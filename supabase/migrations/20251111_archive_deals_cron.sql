-- ========================================
-- Enable required extensions for cron job
-- ========================================

-- Enable pg_net extension for HTTP requests
CREATE EXTENSION IF NOT EXISTS pg_net SCHEMA extensions;

-- Enable pg_cron extension for scheduled jobs
CREATE EXTENSION IF NOT EXISTS pg_cron WITH SCHEMA extensions;

-- ========================================
-- Set up cron job to archive expired deals
-- ========================================

-- Remove existing cron job if it exists
SELECT cron.unschedule('archive-expired-deals-daily');

-- Schedule the archive job to run daily at midnight UTC
-- This will call the archive_deals edge function via HTTP
SELECT
  cron.schedule(
    'archive-expired-deals-daily',
    '0 0 * * *', -- Every day at midnight UTC
    $$
    SELECT
      net.http_post(
        url := (SELECT decrypted_secret FROM vault.decrypted_secrets WHERE name = 'SUPABASE_URL') || '/functions/v1/archive_deals',
        headers := jsonb_build_object(
          'Content-Type', 'application/json',
          'Authorization', 'Bearer ' || (SELECT decrypted_secret FROM vault.decrypted_secrets WHERE name = 'SUPABASE_SERVICE_ROLE_KEY')
        ),
        body := '{}' ::jsonb
      ) AS request_id;
    $$
  );

-- Verify the cron job was created
SELECT * FROM cron.job WHERE jobname = 'archive-expired-deals-daily';
