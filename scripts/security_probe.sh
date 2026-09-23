#!/usr/bin/env bash
# ============================================================================
# EgyptDealRadar - security regression probe
#
# Uses ONLY the public anon key (what any attacker can extract from the APK)
# and checks that nothing sensitive is reachable. Read-only / no-op probes:
# inserts are expected to be rejected; updates/deletes target a non-existent id.
#
# Usage: bash scripts/security_probe.sh      (reads local.properties)
# Exit code 0 = all checks passed.
# ============================================================================
set -u
cd "$(dirname "$0")/.."

prop() { grep "^$1=" local.properties | cut -d= -f2- | tr -d '\r' | sed 's/\\//g'; }
KEY=$(prop SUPABASE_ANON_KEY)
REST=$(prop SUPABASE_PUBLIC_URL | sed 's#/storage.*##')
FN=$(prop SUPABASE_URL | sed 's#/$##')
Z=00000000-0000-0000-0000-000000000000
H=(-H "apikey: $KEY" -H "Authorization: Bearer $KEY" -H "Content-Type: application/json")

fail=0
check() { # name expected actual
  if [[ "$3" =~ ^($2)$ ]]; then echo "PASS  $1 ($3)"; else echo "FAIL  $1 (got $3, expected $2)"; fail=1; fi
}

echo "== Direct table access with the anon key (must be denied or empty)"
for t in users deals votes reports feedback user_identities audit_log rate_limits deal_expiry_votes; do
  body=$(curl -s "$REST/rest/v1/$t?select=*&limit=1" "${H[@]}")
  if [[ "$body" == "[]" || "$body" == *'"code"'* ]]; then echo "PASS  read $t denied/empty"; else echo "FAIL  read $t returned data"; fail=1; fi
done

echo "== Direct writes (must be rejected)"
for t in users deals votes reports feedback; do
  code=$(curl -s -o /dev/null -w "%{http_code}" -X POST "$REST/rest/v1/$t" "${H[@]}" -d "{\"id\":\"$Z\"}")
  check "insert $t" "401|403" "$code"
done

echo "== RPC calls to SECURITY DEFINER functions (must be rejected)"
for f in toggle_vote cast_vote hit_rate_limit purge_old_data increment_approved_deals log_action archive_expired_deals find_similar_deals check_permission; do
  code=$(curl -s -o /dev/null -w "%{http_code}" -X POST "$REST/rest/v1/rpc/$f" "${H[@]}" -d '{}')
  check "rpc $f" "401|403|404" "$code"
done

echo "== Storage upload with anon key (must be rejected)"
code=$(curl -s -o /dev/null -w "%{http_code}" -X POST "$REST/storage/v1/object/deals/images/probe-$RANDOM.jpg" \
  -H "apikey: $KEY" -H "Authorization: Bearer $KEY" -H "Content-Type: image/jpeg" --data-binary "not-an-image")
check "anon storage upload" "400|401|403" "$code"

echo "== Edge functions without a user session (must refuse privileged actions)"
for f in approve_deal reject_deal delete_deal permanent_delete_deal return_to_feed update_user_role get_pending_deals get_reports dismiss_report resolve_report get_user_deals update-deal-image create_upload_url delete_account export_my_data; do
  code=$(curl -s -o /dev/null -w "%{http_code}" -X POST "$FN/$f" "${H[@]}" \
    -d "{\"deal_id\":\"$Z\",\"user_id\":\"$Z\",\"admin_user_id\":\"$Z\",\"moderator_user_id\":\"$Z\",\"target_user_id\":\"$Z\",\"new_role\":\"admin\",\"report_id\":\"$Z\"}")
  check "fn $f without login" "401|403" "$code"
done
for f in submit_deal cast_vote create_report mark_expired check_duplicate link_preview; do
  code=$(curl -s -o /dev/null -w "%{http_code}" -X POST "$FN/$f" "${H[@]}" -d "{\"deal_id\":\"$Z\",\"vote_type\":\"hot\",\"reason\":\"spam\",\"title\":\"x\",\"user_id\":\"$Z\",\"user_email\":\"a@b.c\"}")
  check "fn $f without login" "401|403" "$code"
done

echo "== Internal-only functions (must refuse the anon key)"
# Body is intentionally invalid (no dealId) so nothing could ever be sent.
for f in send_notification maintenance; do
  code=$(curl -s -o /dev/null -w "%{http_code}" -X POST "$FN/$f" "${H[@]}" -d '{}')
  check "fn $f with anon key" "401|403" "$code"
done
echo "== Removed legacy functions (must not exist)"
for f in upload_image cast_vote_once migrate_expires_at archive_deals; do
  code=$(curl -s -o /dev/null -w "%{http_code}" -X POST "$FN/$f" "${H[@]}" -d '{}')
  check "fn $f removed" "404" "$code"
done
echo "== Forged identity in the body must be ignored"
code=$(curl -s -o /dev/null -w "%{http_code}" -X POST "$FN/get_user_profile" "${H[@]}" -d "{\"user_id\":\"$Z\"}")
check "get_user_profile for unknown id without login" "401|404" "$code"
prof=$(curl -s -X POST "$FN/get_user_profile" "${H[@]}" -d "{\"user_id\":\"$Z\"}")
if [[ "$prof" == *"@"* ]]; then echo "FAIL  profile leaks an email"; fail=1; else echo "PASS  profile leaks no email"; fi

echo "== Public feed must not expose private columns"
feed=$(curl -s "$FN/get_deals?limit=5" "${H[@]}")
for col in submitted_by_device submitted_by_user_id approved_by deleted_by email device_id; do
  if [[ "$feed" == *"\"$col\""* ]]; then echo "FAIL  feed exposes $col"; fail=1; else echo "PASS  feed hides $col"; fi
done

echo
if [[ $fail -eq 0 ]]; then echo "ALL SECURITY CHECKS PASSED"; else echo "SECURITY CHECKS FAILED"; fi
exit $fail
