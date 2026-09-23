// ============================================================================
// Google service-account OAuth (used for FCM and Play Integrity)
// Env: FIREBASE_CLIENT_EMAIL, FIREBASE_PRIVATE_KEY (PEM, optionally base64)
// ============================================================================

const cache = new Map<string, { value: string; exp: number }>();

function b64url(input: string | Uint8Array): string {
  const bytes = typeof input === "string" ? new TextEncoder().encode(input) : input;
  let s = "";
  for (const b of bytes) s += String.fromCharCode(b);
  return btoa(s).replace(/\+/g, "-").replace(/\//g, "_").replace(/=+$/, "");
}

function privateKeyPem(): string | null {
  let pem = Deno.env.get("FIREBASE_PRIVATE_KEY");
  if (!pem) return null;
  if (!pem.includes("BEGIN")) pem = atob(pem);
  return pem.replace(/\\n/g, "\n");
}

/** Returns an OAuth access token for `scope`, or null if not configured. */
export async function googleAccessToken(scope: string): Promise<string | null> {
  const hit = cache.get(scope);
  if (hit && hit.exp > Date.now() + 60_000) return hit.value;

  const email = Deno.env.get("FIREBASE_CLIENT_EMAIL");
  const pem = privateKeyPem();
  if (!email || !pem) return null;

  const der = Uint8Array.from(atob(pem.replace(/-----[^-]+-----/g, "").replace(/\s+/g, "")), (c) => c.charCodeAt(0));
  const key = await crypto.subtle.importKey("pkcs8", der, { name: "RSASSA-PKCS1-v1_5", hash: "SHA-256" }, false, ["sign"]);
  const now = Math.floor(Date.now() / 1000);
  const unsigned = b64url(JSON.stringify({ alg: "RS256", typ: "JWT" })) + "." +
    b64url(JSON.stringify({ iss: email, scope, aud: "https://oauth2.googleapis.com/token", iat: now, exp: now + 3600 }));
  const sig = new Uint8Array(await crypto.subtle.sign("RSASSA-PKCS1-v1_5", key, new TextEncoder().encode(unsigned)));

  const res = await fetch("https://oauth2.googleapis.com/token", {
    method: "POST",
    headers: { "Content-Type": "application/x-www-form-urlencoded" },
    body: new URLSearchParams({ grant_type: "urn:ietf:params:oauth:grant-type:jwt-bearer", assertion: `${unsigned}.${b64url(sig)}` }),
  });
  const body = await res.json();
  if (!body.access_token) {
    console.error("Google OAuth failed:", body.error, body.error_description);
    return null;
  }
  cache.set(scope, { value: body.access_token, exp: Date.now() + (body.expires_in ?? 3600) * 1000 });
  return body.access_token;
}
