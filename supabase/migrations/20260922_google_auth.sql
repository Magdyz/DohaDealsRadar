-- ============================================================================
-- Google-only sign-in
--
-- Identity now comes from Google through Supabase Auth, so our own table no
-- longer needs the email address. New profiles are created without one, and a
-- legacy profile's email is cleared the first time its owner signs in with
-- Google (see functions/sign_in_with_google).
-- ============================================================================

alter table public.users alter column email drop not null;

-- One profile per auth user (the only link we keep).
create unique index if not exists users_auth_user_id_key
  on public.users (auth_user_id) where auth_user_id is not null;

-- The old unique constraint on email must tolerate many NULLs (it already does
-- in Postgres), but make sure it is not the primary way we find people.
create index if not exists users_email_lookup
  on public.users (lower(email)) where email is not null;
