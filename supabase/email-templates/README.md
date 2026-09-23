# Supabase Auth email templates

Used for the email verification code (OTP) sent when a user logs in to the app.

## Where to paste

Supabase dashboard → **Authentication → Emails → Templates**

Paste into **both** templates:
- **Confirm signup** (first login with a new email)
- **Magic Link** (returning users)

For each template:
1. **Subject** → paste the subject below.
2. **Message body** → switch to **Source**, delete everything, paste the full contents of `verification-code.html`.
3. **Save**.

## Subject

```
Your EgyptDealRadar code: {{ .Token }}
```

## Notes

- `{{ .Token }}` is the 6-digit code the app asks for. Keep it exactly as written.
- The email says the code expires in **10 minutes**. Make this match
  **Authentication → Providers → Email → Email OTP Expiration** = `600` seconds
  (Supabase default is 3600 = 1 hour).
- Preview: open `verification-code.html` in a browser (the code shows as `{{ .Token }}`).
