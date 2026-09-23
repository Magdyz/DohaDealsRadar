-- ============================================================================
-- Admin oversight (2026-09-25)
--   1. client_errors: anonymous app error / crash counts for "App health"
--      (no user ids, no free text - only where + error code + app version).
--      Purged after 30 days by the maintenance function.
--   2. Indexes for the admin screens (audit log, feedback, user search).
-- Run in the Supabase SQL editor. Apply BEFORE deploying the new functions.
-- ============================================================================

create table if not exists public.client_errors (
  id          bigint generated always as identity primary key,
  created_at  timestamptz not null default now(),
  kind        text not null check (kind in ('crash', 'error')),
  area        text not null,          -- e.g. feed_load, post_submit, vote
  code        text,                   -- error code / exception class, never message text
  app_version text,
  os_version  text
);
create index if not exists client_errors_created_idx on public.client_errors (created_at desc);

alter table public.client_errors enable row level security;   -- no policies: service role only
revoke all on public.client_errors from anon, authenticated;

create index if not exists audit_log_created_idx on public.audit_log (created_at desc);
create index if not exists audit_log_action_created_idx on public.audit_log (action_type, created_at desc);
create index if not exists feedback_status_created_idx on public.feedback (status, created_at desc);
create index if not exists users_created_idx on public.users (created_at desc);
