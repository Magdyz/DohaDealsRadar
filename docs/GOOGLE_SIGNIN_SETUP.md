# Turning on "Sign in with Google" — step by step

The app code is finished. It needs three things from you, all free, about 15
minutes in total. Until they are done, the sign-in screen shows
*"Google sign-in isn't set up in this build yet."*

Your Firebase project (`dohadeals-3d6b1`) is also a Google Cloud project, so
everything below happens inside the project you already own.

---

## Step 1 — Create the two OAuth client IDs (Google Cloud)

You do this once, in the Google Cloud project that already backs your Firebase
project. Nothing here costs money.

### 1a. Open the right project

1. Go to <https://console.cloud.google.com/>.
2. Click the project selector in the blue bar at the top.
3. Choose **dohadeals-3d6b1**. Every screen below must show that name.

### 1b. Set up the consent screen (once, before any client)

Google won't let you create a client until the app has a consent screen.

1. In the search bar at the top, type **Google Auth Platform** and open it.
   (Older console: **APIs & Services → OAuth consent screen** — same thing.)
2. If you see a **Get started** button, click it and fill in:
   - **App name:** `EgyptDealRadar`
   - **User support email:** your own email (pick it from the dropdown)
   - **Audience:** choose **External**
   - **Contact information:** your email again
   - Tick the agreement, then **Create**.
3. On **Branding**, fill **Developer contact information → Email addresses**
   and click **Save**. Skip the app logo: uploading one forces your app into
   Google's verification queue.

4. **To publish you also need two URLs** that the Branding page marks as
   optional. The "Publish app" button stays greyed out without them, and the
   error message never says which ones — the button's own tooltip does:
   *"Valid app name, support email, homepage url, and privacy policy url are
   required."* So on **Branding**:
   - **Authorized domains** → add your domain **first** (Google rejects the URLs
     otherwise). For GitHub Pages that is `magdyz.github.io`.
   - **Application home page** → `https://magdyz.github.io/DohaDealsRadar/`
   - **Application privacy policy link** →
     `https://magdyz.github.io/DohaDealsRadar/privacy-policy.html`
   - **Terms of service** can stay empty.
   - **Save**, then go to **Audience → Publish app**.

   Those two pages live in this repo (`docs/index.html` and
   `docs/privacy-policy.html`); publishing them is the same GitHub Pages step
   Play needs for its privacy-policy URL, so it covers both at once.

5. Don't add any scopes on the Data Access page. The defaults are right, and
   with only name/email/profile there is no Google review to pass.

**You can test before any of that.** Leave the status on *Testing* and add your
own address under **Audience → Test users**. Sign-in then works for that account
immediately. Publishing is only required before real users install the update:
in Testing, anyone not on that list is refused, and the list is capped at 100
accounts for the app's lifetime.

### 1c. Client 1 — the Web client (the one the app and Supabase use)

1. Left menu → **Clients** → **Create client**.
   (Older console: **Credentials → Create credentials → OAuth client ID**.)
2. **Application type:** `Web application`
3. **Name:** `EgyptDealRadar backend`
4. Under **Authorized redirect URIs**, click **Add URI** and paste:
   ```
   https://nzchbnshkrkdqpcawohu.supabase.co/auth/v1/callback
   ```
   (The Android flow does not use it, but Supabase expects it to exist.)
5. Click **Create**.
6. A dialog shows **Client ID** and **Client secret**. Copy both somewhere safe
   now — you need the ID for the app and both for Supabase. You can always
   reopen the client later to see them again.

The **Client ID** looks like `123456789012-abc123def456.apps.googleusercontent.com`.
It is not a secret. The **Client secret** is — keep it out of chats, screenshots
and the repo; it only ever goes into the Supabase dashboard.

### 1d. Client 2 — the Android client (proves the request came from your app)

1. **Clients → Create client** again.
2. **Application type:** `Android`
3. **Name:** `EgyptDealRadar Android`
4. **Package name:** `qa.deals.doha`
   - This is the published package, **not** `eg.deals.radar`. The code
     namespace changed; the Play package never can.
5. **SHA-1 certificate fingerprint:** paste your debug fingerprint:
   ```
   6B:73:F4:EE:FF:A6:A7:D3:24:99:EC:4C:D9:4D:5E:0F:3D:DE:EB:B6
   ```
6. **Create**.

That covers builds from Android Studio on this machine. Real users install a
copy signed by Google, so it needs its own client:

7. **Clients → Create client** a third time, same type and package name, and
   paste the **Play app signing** SHA-1 (next section). Name it
   `EgyptDealRadar Android (Play)`.

> Android clients have no secret, and one fingerprint per client — that is why
> the Play certificate needs its own entry.

### 1e. Where to find the Play app-signing SHA-1

1. Open <https://play.google.com/console/> and select the app.
2. Left menu → **Test and release → Setup → App signing**.
   (Older layout: **Release → Setup → App integrity → App signing**.)
3. Under **App signing key certificate**, copy the **SHA-1 certificate
   fingerprint** (a row of 20 hex pairs).
4. Optional but handy: the same page shows the **Upload key certificate**
   SHA-1. Add that one too, as a fourth client, if you ever install a signed
   build on your phone directly instead of through Play.

Skipping this step is the classic mistake: sign-in works perfectly on your
machine and fails for everyone who installs from the Play Store.

New fingerprints can take a few minutes to take effect.

---

## Step 2 — Enable Google in Supabase

Supabase dashboard → your project → **Authentication → Providers → Google**:

1. Toggle **Enable Sign in with Google**.
2. **Client ID**: the *Web* client ID from step 1.
3. **Client Secret**: the *Web* client secret.
4. Open **Authorized Client IDs** and paste the *Web* client ID there as well
   (this is what allows the native Android token to be accepted). If you also
   want to allow the Android client directly, add its client ID on a new line.
5. Save.

Leave "Skip nonce check" **off** — the app sends a proper nonce.

## Step 3 — Put the Web client ID in the app

Open `local.properties` in the project root (it is never committed) and add one
line:

```
GOOGLE_WEB_CLIENT_ID=123456789-xxxxxxxxxxxxxxxx.apps.googleusercontent.com
```

Use the **Web** client ID, not the Android one — this is the single most common
mistake. Then rebuild the app.

For GitHub Actions, add the same value as a repository secret named
`GOOGLE_WEB_CLIENT_ID` (the workflow already passes it through).

---

## Testing it

1. Make sure the emulator or phone has a Google account added
   (Settings → Passwords & accounts → Add account).
2. Open the app → tap the account button or try to post → tick the consent box →
   **Continue with Google** → pick an account.
3. You should land back in the app signed in, with a random username like
   `DealHunter42`.

If something fails, the message tells you which part:

| Message | What it means |
|---|---|
| "Google sign-in isn't set up in this build yet." | `GOOGLE_WEB_CLIENT_ID` missing from `local.properties` — rebuild after adding it. |
| "No Google account found on this phone." | Add a Google account in the phone's settings. |
| "We couldn't sign you in with Google." | The account picker failed: usually the SHA-1 or package name in the Android client doesn't match the build you are running. |
| "We couldn't verify your Google account." | The token reached our backend but Supabase rejected it: check that the *Web* client ID is in Supabase's **Authorized Client IDs**. |

---

## What happens to existing accounts

People who signed in before with an email code keep their account, their deals
and their username: the first time they sign in with Google using the same
address, the old profile is adopted and its email is then cleared from our
database. Nobody loses anything, and nothing needs to be migrated by hand.

## After it works

The old email-code functions (`send-verification-code`,
`verify-code-and-get-user`) are still deployed but the app no longer calls them.
Once you have signed in with Google on a real phone, they can be deleted:

```bash
npx supabase functions delete send-verification-code --project-ref nzchbnshkrkdqpcawohu
npx supabase functions delete verify-code-and-get-user --project-ref nzchbnshkrkdqpcawohu
```

Deleting them also removes the last reason to configure custom SMTP.

One caveat: `scripts/backend_e2e.mjs` signs its test users in through
`verify-code-and-get-user`, so delete that function only when you no longer need
the suite, or switch the script to create sessions with the admin API first.
