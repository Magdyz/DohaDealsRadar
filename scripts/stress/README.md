# Stress-testing EgyptDealRadar's backend

Everything here targets a **STAGING** Supabase project only. The production
project ref is `nzchbnshkrkdqpcawohu` (see `local.properties` and
`supabase/migrations/20260922_security_hardening.sql`'s cron job URL) -
`seed_staging.sql`, `k6_feed_vote_post.js` and `make_tokens.mjs` all refuse
to run if that ref shows up in their config.

Files in this directory:

| File | What it does |
|---|---|
| `seed_staging.sql` | Bulk-generates ~20k users / ~50k deals / ~500k votes / ~5k reports directly in the app tables. |
| `make_tokens.mjs` | Creates a smaller set of **real** logged-in accounts (Auth + profile) and writes their access tokens for k6. |
| `k6_feed_vote_post.js` | The actual load test: browse / vote / post / account traffic, plus a "hot deal" contention scenario. |
| `explain_top_queries.sql` | `EXPLAIN (ANALYZE, BUFFERS)` for the app's hottest query shapes, plus index/table-size diagnostics. |
| `.gitignore` | Keeps `tokens.json` / `tokens_meta.json` / k6 output out of git (they contain live staging bearer tokens). |

## 0. A honest caveat about the schema

`supabase/migrations/` only goes back to `20251111_archive_deals_cron.sql`.
The original `CREATE TABLE` statements for `public.users`, `public.deals`,
`public.votes`, `public.reports`, `public.feedback`, `public.user_identities`
and `public.audit_log` are **not in this repo** - they were applied by hand
directly against the production database at some point before that. This
README can't hand you a single authoritative "run this to get the base
schema" script for that reason. Two ways to get a staging project to the
same starting point, in order of preference:

- **Best: clone the schema from production.** With the Postgres connection
  string from Supabase Dashboard > Project Settings > Database (production
  project), on a machine you trust:
  ```
  pg_dump "postgresql://postgres:<password>@<prod-host>:5432/postgres" \
    --schema-only --schema=public --no-owner --no-privileges \
    -f schema.sql
  ```
  Review `schema.sql` (make sure it's schema-only, no `COPY` data blocks),
  then restore it into the empty staging project with `psql` or the SQL
  editor. This is the only way to get byte-for-byte accurate base tables.
- **Fallback:** use the Supabase Dashboard's "Duplicate project" option if
  it's available on your plan (Settings > General) to fork production's
  schema into a new project, then immediately disconnect it from
  production-adjacent settings (custom domain, webhooks, cron secrets).

Whichever way you get there, treat the result as the "pre-2025-11-11"
baseline and apply the migrations below **on top of it**.

## 1. Create the staging project

Supabase Dashboard > New Project, in its own organization/billing if
possible so it can never share quota with production. Note its project ref
(you'll need it constantly below) and, from Project Settings > API:
`anon` key, `service_role` key, and the project URL.

## 2. Apply migrations, in this order

Two ordering hazards in this repo that aren't obvious from the filenames:

1. **`create_feedback_table.sql` has no date prefix**, so a plain
   alphabetical `ls | sort` puts it *after* every `2025xx...sql` /
   `2026xx...sql` file. It must actually run early:
   `20260922_security_hardening.sql` does
   `drop policy if exists "..." on public.feedback` and
   `alter table public.feedback enable row level security`, both of which
   **fail if the table doesn't exist yet** (`IF EXISTS` only guards the
   policy name, not the table).
2. **`20251120_fix_cast_vote_atomic_ambiguity.sql` must run *after*
   `20251120_vote_switching_transaction.sql`**, even though "fix" sorts
   alphabetically before "vote_switching" on the same date.
   `vote_switching_transaction.sql` is what *creates* `cast_vote_atomic()`;
   `fix_cast_vote_atomic_ambiguity.sql` only patches it afterwards. Applied
   in alphabetical order, the "fix" would create the function first (already
   correct) and then `vote_switching_transaction.sql` would immediately
   overwrite it with the pre-fix body. In practice this function is dead
   code today (`cast_vote`/`index.ts` calls `toggle_vote()`, not
   `cast_vote_atomic()`) so getting it wrong is harmless - but get it right
   anyway for a clean history.

Recommended order:

```
create_feedback_table.sql
20251111_archive_deals_cron_alternative.sql   -- see note below; SKIP the non-"_alternative" one
20251116_add_price_fields.sql
20251119_add_user_id_to_votes.sql
20251120_cleanup_votes_without_user_id.sql
20251120_vote_switching_transaction.sql       -- before the ambiguity fix, see hazard #2
20251120_fix_cast_vote_atomic_ambiguity.sql
20251120_fix_votes_identifier_constraint.sql
20251121_fix_device_id_constraint.sql
20251127_add_category_index.sql
20260922_constraints_egypt.sql
20260922_google_auth.sql
20260922_security_hardening.sql               -- EDIT before applying, see below
20260923_duplicate_detection_link_only.sql
20260924_scale_tier1.sql
```

Notes on the two `2025111_archive_deals_cron*` files:

- `20251111_archive_deals_cron.sql` (no suffix) sets up a `pg_net` cron job
  that calls a `/functions/v1/archive_deals` Edge Function. That function
  **doesn't exist anymore** in `supabase/functions/` - it was replaced by
  `maintenance`. Applying this file just gets you a cron job that 404s every
  night. **Skip it** on staging.
- `20251111_archive_deals_cron_alternative.sql` defines the
  `archive_expired_deals()` SQL function, which **is** still needed - the
  `maintenance` Edge Function calls it via `db.rpc("archive_expired_deals")`
  (see `supabase/functions/maintenance/index.ts`). Apply this one. It also
  schedules its own `archive-expired-deals-daily` cron job calling
  `archive_expired_deals()` directly; that becomes redundant once step 3
  below sets up `daily-maintenance` (which calls `archive_expired_deals()`
  as part of the fuller `maintenance` function) - harmless to leave both,
  or run `select cron.unschedule('archive-expired-deals-daily');` after
  everything else to avoid double-archiving every night.

**Before applying `20260922_security_hardening.sql`**, edit section 11 near
the bottom (`daily-maintenance` cron job) - it hardcodes the **production**
URL `https://nzchbnshkrkdqpcawohu.supabase.co/functions/v1/maintenance`.
Either change it to your staging project's URL, or comment the whole
`cron.schedule('daily-maintenance', ...)` block out (a throwaway staging
project doesn't need nightly retention purges running against itself, and
you must never let it fire against production by accident). If you do keep
it, also set a `cron_secret` value in Supabase Vault (Database > Vault) and
the same value as the `CRON_SECRET` env var on the deployed `maintenance`
function (step 3).

Apply each file with `psql "$STAGING_DB_URL" -v ON_ERROR_STOP=1 -f <file>`
or paste them into the Supabase SQL editor one at a time, in the order
above.

## 3. Deploy the Edge Functions

```
supabase functions deploy --project-ref <staging-ref>
```
deploys everything in `supabase/functions/`. Recommended function secrets
for a load-testing project (Dashboard > Edge Functions > Secrets, or
`supabase secrets set --project-ref <staging-ref> KEY=value`):

- `PLAY_INTEGRITY_MODE=off` - staging traffic (k6, emulators) won't carry
  real Play Integrity tokens; leave this off so `submit_deal`/`cast_vote`/
  `create_report` don't start rejecting load-test traffic (see
  `supabase/functions/_shared/integrity.ts`).
- `CRON_SECRET` - only needed if you kept the `daily-maintenance` cron job
  from step 2; must match the `cron_secret` Vault entry.
- `SAFE_BROWSING_API_KEY` - leave unset; `isUnsafeUrl()` just no-ops without
  it, which is fine for synthetic deal links.
- FCM / push notification secrets - leave unset; `background(notifyNewDeal(...))`
  swallows its own errors and never blocks the request.

`SUPABASE_URL` and `SUPABASE_SERVICE_ROLE_KEY` are injected automatically by
the platform for every deployed function - don't set them yourself.

## 4. Mark the project as staging

In the Supabase SQL editor, on the **staging** project only:

```sql
create table public.staging_marker (
  created_at timestamptz not null default now(),
  note text
);
insert into public.staging_marker (note) values ('staging - safe to seed/wipe');
```

`seed_staging.sql`'s first statement aborts if this table doesn't exist.
Never create it on production.

## 5. Seed bulk data

```
psql "$STAGING_DB_URL" -v ON_ERROR_STOP=1 -f scripts/stress/seed_staging.sql
```
(or paste the file into the SQL editor - it stops on the first error there
too). Takes a few minutes for the default ~20k/50k/500k/5k row counts;
tune the three `generate_series(1, N)` calls in the file to scale up/down.
It prints a summary of how many seeded rows landed in each table at the
end. The commented-out cleanup block at the bottom removes everything it
created (`users.email like 'seed+%@example.com'`, `deals.title like
'[SEED]%'` and everything joined to them).

## 6. Create real accounts + tokens for k6

k6 needs actual logged-in sessions (bearer tokens), not the bulk seed rows
from step 5 (those have no `auth_user_id` and can't log in). Generate them:

```
# PowerShell
$env:SUPABASE_URL         = "https://<staging-ref>.functions.supabase.co"
$env:SUPABASE_PROJECT_URL = "https://<staging-ref>.supabase.co"
$env:ANON_KEY             = "<staging anon key>"
$env:SERVICE_KEY          = "<staging service_role key>"
$env:N                    = "800"
node scripts/stress/make_tokens.mjs
```

Writes `scripts/stress/tokens.json` (array of access tokens, consumed
directly by k6) and `tokens_meta.json` (needed for cleanup later - keep
both out of git, `.gitignore` here already does that). ~800 tokens covers
the default k6 config's peak of 500 VUs in `main` + 200 VUs in `hot_deal`
running concurrently, with some headroom.

Each created account is promoted to `trust_level='trusted'` +
`auto_approve=true` right after creation (see the script's header comment
for why: a brand-new account's `submit_deal` quota is 3/day, which would
starve the k6 "post" scenario almost immediately).

Cleanup when you're done:
```
$env:CLEANUP = "1"
node scripts/stress/make_tokens.mjs
```

## 7. Pick a "hot deal" id

For the contention scenario (200 VUs voting on the same row), grab any live
deal id from staging, e.g. in the SQL editor:
```sql
select id, title, hot_count, cold_count
  from deals
 where status = 'approved' and is_archived = false
 order by created_at desc
 limit 1;
```

## 8. Install k6 on Windows

```
winget install k6 --source winget
```
or `choco install k6`, or download the zip from https://k6.io/docs/get-started/installation/
and put `k6.exe` on your `PATH`. Verify with `k6 version`.

## 9. Run the load test

Run these from the repo root (k6's `open()` for `TOKENS_FILE` resolves
relative to the current working directory at runtime, not the script's
location):

```
# PowerShell
$env:SUPABASE_URL  = "https://<staging-ref>.functions.supabase.co"
$env:ANON_KEY      = "<staging anon key>"
$env:TOKENS_FILE   = "scripts/stress/tokens.json"
$env:HOT_DEAL_ID   = "<uuid from step 7>"
k6 run scripts/stress/k6_feed_vote_post.js
```
```
# bash / git-bash
SUPABASE_URL=https://<staging-ref>.functions.supabase.co \
ANON_KEY=... TOKENS_FILE=scripts/stress/tokens.json HOT_DEAL_ID=<uuid> \
k6 run scripts/stress/k6_feed_vote_post.js
```

`SUPABASE_URL`, `ANON_KEY` and `TOKENS_FILE` are required; the script
refuses to start without them, and refuses outright if `SUPABASE_URL`
contains the production ref. `HOT_DEAL_ID` is optional - without it, the
`hot_deal` scenario is skipped (main browse/vote/post/account traffic still
runs).

Add `--summary-export=scripts/stress/k6-summary.json` to get a machine-
readable summary, or `-o json=scripts/stress/k6-results.json` for
per-request data. Both are already covered by `.gitignore` here.

To run a smaller smoke test first, override the VU stages without editing
the script: `k6 run --vus 10 --duration 1m scripts/stress/k6_feed_vote_post.js`
(this replaces the `scenarios` block's ramp with a single flat stage - fine
for a sanity check, not for reading the p95 thresholds meaningfully).

## 10. Reading the results

- **k6's own summary** at the end of the run shows `http_req_duration`
  percentiles per tagged endpoint (`{name:get_deals}`, `{name:cast_vote}`,
  `{name:submit_deal}`, `{name:create_upload_url}`, `{name:get_user_deals}`,
  `{name:storage_put}`) and whether the three `thresholds` in the script
  (`get_deals` p95 < 300ms, `cast_vote` p95 < 400ms, `submit_deal` p95 <
  1500ms, overall `http_req_failed` < 1%) passed or failed.
- **Important:** the script calls
  `http.setResponseCallback(http.expectedStatuses({min: 200, max: 499}))`,
  so `http_req_failed` only counts network errors / status 0 / 5xx. Expected
  app-level responses (`RATE_LIMITED` 429, `OWN_DEAL`/`DEAL_UNAVAILABLE` 4xx,
  `DUPLICATE_DEAL` 409, etc.) do **not** count against that 1% budget - they're
  asserted individually via `check()` instead. If you want to know how often
  those happen, read the `checks` block in the summary (e.g. "cast_vote
  handled" includes both 200s and expected 4xx/429s - compare it against a
  narrower check if you need the split).
- **Database side** (Supabase Dashboard > Reports / Database, on the
  staging project): CPU %, active connections vs. the pool's max connections
  (Database > Settings > Connection pooling), disk IOPS if available.
  Watch connections especially during the `hot_deal` scenario - 200 VUs all
  landing on `toggle_vote()`'s `select ... for update` on the *same* `deals`
  row will serialize there; if `http_req_duration{name:cast_vote}` p95
  balloons specifically for the duration `hot_deal` is active, that's the
  expected row-lock contention, not a connection problem.
- **`scripts/stress/explain_top_queries.sql`**: run it once before the k6
  run and again right after, and diff section 7's `idx_scan` counts to
  confirm the feed/search/vote paths actually used the indexes you expect
  (`deals_feed_hot_idx`, the trigram indexes, `deals_submitted_by_idx`,
  `idx_votes_user_deal`). Section 8's `n_dead_tup` on `deals` is worth a
  second look after a heavy voting run, since every vote is an `UPDATE
  deals SET hot_count/cold_count`.
- **`public.rate_limits`** table growth (`select count(*) from
  rate_limits;`) is a rough proxy for how much traffic is being throttled;
  each throttled key gets one row per fixed window.

## 11. Android-side checks

This repo doesn't currently have a Macrobenchmark module (`settings.gradle.kts`
only declares `:app`, `:core:*` and `:feature:*`) - if you want automated
startup/scroll benchmarks, add one first via Android Studio's File > New >
New Module > Benchmark wizard (targeting `:app`), pointed at a debug/staging
build variant. Until then, the checks below are manual, against a device or
emulator running a build configured to hit the **staging** project (swap
`local.properties`' `SUPABASE_URL`/`SUPABASE_ANON_KEY`/`SUPABASE_PUBLIC_URL`/
`SUPABASE_STORAGE_URL` for staging's values before building, and swap them
back afterward - don't ship a build pointed at staging, and don't point a
benchmark at production).

Do this with `seed_staging.sql` already run, so the feed has ~50k deals of
realistic-length titles/descriptions and real (picsum.photos) images to
scroll through and load, not an empty list.

- **Cold/warm startup and feed-scroll timing**: once a Macrobenchmark module
  exists, run its `StartupBenchmark`/`ScrollBenchmark` against the feed
  screen; until then, use Android Studio's Profiler (Run > Profile) or
  `adb shell am start -W qa.deals.doha/.MainActivity` for a rough
  cold-start number, and watch the Profiler's frame-timing / jank overlay
  while fling-scrolling the seeded feed.
- **Throttled network (3G-like)**: on an emulator, Extended Controls (`...`
  in the emulator toolbar) > Cellular > set Network type to "3G"/"LTE" and
  adjust Signal strength, or launch the emulator with
  `emulator -avd <name> -netspeed gsm -netdelay gsm` for a slower profile
  from the start. Confirm the feed still loads (with a visible loading
  state) and that `get_deals`/image requests don't time out the UI.
- **Offline**: Extended Controls > Cellular > Data status "Denied"/None (or
  `adb shell svc wifi disable && adb shell svc data disable`, `svc wifi
  enable`/`svc data enable` to restore). Confirm the app shows a clear
  offline/error state instead of hanging or crashing, and recovers cleanly
  once connectivity returns.
- **Kill mid-post**: start posting a deal (through create_upload_url's photo
  picker/upload step or right after tapping "Post"), then run
  `adb shell am kill qa.deals.doha` (applicationId from
  `app/build.gradle.kts`) while the request is in flight. Relaunch the app
  and check: no crash loop, no duplicate deal on retry (the `check_duplicate`
  /`DUPLICATE_DEAL` path in `submit_deal` should catch a resubmit of the
  same link), and no orphaned photo left in Storage under
  `images/<authUserId>/` with no deal pointing at it (the nightly
  `purge_old_data()` doesn't clean up storage objects that were never
  attached to a deal row at all, only images belonging to deleted/rejected
  deals - so check manually here).

## 12. Cleanup

1. k6's own load-test deals: `delete from deals where title like '[LOADTEST]%';`
   (also uncommented at the bottom of `seed_staging.sql`'s cleanup block).
2. Bulk seed data: uncomment and run the cleanup block at the bottom of
   `seed_staging.sql`.
3. Token accounts: `CLEANUP=1 node scripts/stress/make_tokens.mjs` (needs
   `tokens_meta.json` from step 6 to still be present).
4. If you keep the staging project around between runs, `vacuum analyze;`
   afterward so the next `explain_top_queries.sql` run reflects a clean
   state rather than post-load bloat.
