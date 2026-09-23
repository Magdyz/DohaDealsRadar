# EgyptDealRadar 2.0 — what was done, and what is left for you

Date: 2026-09-22 · Branch: `egypt-migration` (nothing committed yet)

---

## Done

### Security (the audit's critical findings are all closed)

- **Real logins.** The email code now returns a real session (access + refresh token). The app stores
  it encrypted with a key held in the Android Keystore, refreshes it automatically, and logs you out
  cleanly when it expires. The backend now reads *who you are* from the token only — never from the
  request body, which is what previously let anyone act as an admin.
- **The public key can no longer read or write anything directly.** Emails, device IDs and votes are
  closed off; uploads go through short-lived signed URLs; every table and function privilege was
  revoked from the public roles. Photos are limited to 3 MB, JPEG/WebP only.
- **Every moderator action checks the caller's role** on the server.
- **Rate limits** on posting, voting, reporting, sign-in codes, duplicate checks and feedback.
- **Link safety.** Only `https`, no IP addresses, no internal hosts, no URL shorteners, with a DNS
  check against private ranges. Optional Google Safe Browsing lookup (see "Optional" below).
- **Abuse handling.** Three reports auto-hide a deal until a moderator looks; rejections add a strike;
  five clean approvals earn auto-publish; strikes take it away.
- Verified by `scripts/security_probe.sh` (all pass) and `scripts/backend_e2e.mjs` (59/59 pass).

### Sign in with Google (replaces emailed codes)

- One tap through the Android system account picker: no password typed, no code to wait for.
- The Google ID token is verified on our backend, which returns the same secure session as before.
- **We never store the email.** New profiles hold only a random username and an internal id; the
  identity stays inside the sign-in service. Anyone who signed in before by email keeps their
  account, and that stored email is cleared the first time they sign in with Google.
- A nonce is generated per sign-in and checked server-side, so a stolen token can't be replayed.
- Setup steps: [GOOGLE_SIGNIN_SETUP.md](GOOGLE_SIGNIN_SETUP.md).

### Privacy — no data collection model

- PostHog and Firebase Analytics **removed entirely** (SDKs, keys, calls and ProGuard rules).
- Storage and photo permissions removed: the system photo picker needs neither.
- Every statistic in the app is now an anonymous aggregate from your own database.
- In-app privacy policy in English and Arabic (updated for Google sign-in), plus "Download my data" and "Delete my account",
  both tested end to end on a device.
- Retention: expired deals purged after 90 days, rejected after 30, reports after 90, feedback after
  1 year — run nightly by a scheduled job.

### Egypt

- Egyptian pounds everywhere, 8 Egypt categories, 27 governorates plus "All Egypt / Online".
- Governorate filter in the feed and a governorate picker when posting.
- Full Arabic with one-tap switching, Cairo font, right-to-left layout. Admin screens stay English.
- Store names recognised for Egyptian retailers (Jumia, Noon, Carrefour, Talabat, B.TECH and more).

### No duplicate deals

Three independent checks, all run **before** the photo is uploaded so a blocked post wastes nothing:

1. the same link (tracking parameters stripped, Amazon ASIN / noon SKU normalised),
2. the same photo (perceptual fingerprint, so a re-crop or re-save still matches),
3. a very similar title (trigram similarity).

Exact matches are blocked with "Already posted — open it instead"; similar ones ask "is yours
different?". Verified on the device: the same link with a tracking tag and the same photo under a
different link were both caught.

### Speed and size

- Room moved from kapt to KSP (faster builds).
- Unused dependencies dropped (two Accompanist libraries, a duplicated Compose foundation).
- Six unused Inter font weights deleted (~2 MB).
- Hundreds of stray macOS `._*` files removed from the project — these were **breaking the release
  build**, so release builds now work again.
- Server-side keyset pagination, indexed feed queries, one image upload per post.

### Fixed along the way (found while testing on the emulator)

- Timestamps were read as local time, so in Egypt every deal looked 2–3 hours old. All date parsing
  now goes through one UTC-correct helper, with unit tests.
- New-deal notifications defaulted to **off** and nothing ever turned them on — users would never
  have received a single alert. They are now on by default once notification permission is granted,
  and still switchable off in settings.
- English deal titles rendered scrambled in Arabic mode (bidirectional text).
- Vote counts were clipped inside their circles in Arabic.
- An invisible white "My Deals" heading on the account screen.
- Role changes needed a fresh login to take effect; the profile now refreshes on every launch.
- Online deals showed "No location" instead of the store name.
- The posting dialog still listed an upload step that no longer exists.

### Play Console's four recommended actions

All four were fixed in this build:

- **"Outdated SDK: androidx.fragment"** — it came in through Credential Manager's
  `play-services-auth`, which still asks for fragment 1.5.7. Pinned to 1.8.6, so the
  whole graph resolves to the current version.
- **"Deprecated APIs for edge-to-edge"** and **"Edge-to-edge may not display for all
  users"** — the app already called `enableEdgeToEdge()` and never touched the
  deprecated window-colour setters, but androidx.activity 1.9.2 did internally.
  Upgraded to 1.10.1, which dropped those calls; the feature module no longer pins
  its own older copy.
- **"Remove resizability and orientation restrictions"** — dropped
  `screenOrientation="portrait"`, added `resizeableActivity="true"`, and widened
  `configChanges` so rotation and window resizing are handled without recreating
  the activity. The app now works in landscape and in split-screen on tablets and
  foldables. (Android 16 ignores orientation locks on large screens anyway, so
  this was going to happen with or without the manifest entry.)

### Tests and CI

- Unit tests for money formatting, governorates/categories and timestamp parsing.
- `.github/workflows/android.yml`: unit tests, debug build and **release build (R8 + lint)** on every
  push and pull request, with the backend end-to-end suite available on demand.

---

## What you need to do

### Before publishing the update

1. **Turn on Google sign-in.** Sign-in is now "Continue with Google" only: no emails, no codes, and
   the Supabase SMTP rate limit no longer applies to anything. It needs two OAuth client IDs in
   Google Cloud, the Google provider switched on in Supabase, and one line in `local.properties` —
   all written out step by step in [GOOGLE_SIGNIN_SETUP.md](GOOGLE_SIGNIN_SETUP.md). Until that is
   done the app says so plainly on the sign-in screen.
2. **Privacy policy URL.** Play requires a public link. `docs/privacy-policy.html` is written and
   ready; host it (GitHub Pages is free — instructions in `docs/play-store-listing.md`).
3. **Store listing.** Title, both descriptions, release notes, Data Safety answers and screenshot
   guidance are all written out in `docs/play-store-listing.md`. Update the images and description in
   Play Console as you planned.
4. **Make yourself an admin.** Sign in with Google in the app **first**, then run this once in the
   Supabase SQL editor with the address of the Google account you used:
   ```sql
   update public.users u
      set role = 'admin', auto_approve = true
     from auth.users a
    where u.auth_user_id = a.id
      and a.email = 'you@example.com';
   ```
   (The app's own table no longer stores emails, so the lookup goes through the sign-in service.)
   Everyone, including you, signs in again after this update, because the old app had no real
   sessions.
5. **Keep the Firebase Android app for `qa.deals.doha`.** The Play listing's package name cannot
   change, so notifications depend on that entry still existing in the Firebase project. If you
   removed it earlier, add it back and re-download `google-services.json`.
6. **Signing.** `app/build.gradle.kts` has no signing config, so use Android Studio's
   *Build → Generate Signed App Bundle* with your existing keystore — the same one the current listing
   uses, otherwise Play will reject the upload.

### Optional, when you want it

- **Play Integrity** (blocks bots and modified apps): link the Cloud project in Play Console, enable
  the Play Integrity API, then set the function secret `PLAY_INTEGRITY_MODE` to `monitor` first and
  `enforce` once the logs look clean. It is `off` today.
- **Safe Browsing** (extra scam-link protection): create a Google API key and set it as the function
  secret `SAFE_BROWSING_API_KEY`.
- **A domain name.** A domain is simply a web address you rent (about $10–15 a year), like
  `egyptdealradar.com`. You do not need one to publish: it only gives you a nicer privacy-policy link,
  shareable deal links, and an email address like `hello@egyptdealradar.com` instead of a personal one.
- **A generated baseline profile.** The current one is hand-written. A real one (from a macrobenchmark
  run) would shave more off cold start.
- **Toolchain upgrade.** AGP 8.7 / Kotlin 2.0 / Compose BOM 2024.10 are stable and building cleanly.
  Newer versions exist; upgrading is worth doing in a quiet week, not the week you ship.

### Housekeeping

- The Firebase service-account key file in the project folder is gitignored but should be moved
  somewhere outside the repo or deleted once the functions are deployed.
- `local.properties` currently points at the Windows SDK path for this machine. It is gitignored, so
  it does not affect the repo; Android Studio fixes it automatically on another machine.
- Nothing is committed. All the work sits on the `egypt-migration` branch, uncommitted, as you asked.
