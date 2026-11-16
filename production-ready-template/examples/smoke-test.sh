#!/bin/bash
# Smoke Test Script — Run after `docker compose up -d --wait`
# Exit codes: 0 = all pass, 1 = any fail

set -euo pipefail

BASE_URL="${BASE_URL:-http://127.0.0.1:8080}"
OPS_KEY="${INTERNAL_OPS_API_KEY:-dev-ops-key-change-me}"

echo "=== Smoke Test: $BASE_URL ==="

# Colors
GREEN='\033[0;32m'
RED='\033[0;31m'
NC='\033[0m'

pass() { echo -e "${GREEN}✅ $1${NC}"; }
fail() { echo -e "${RED}❌ $1${NC}"; exit 1; }

# 1. Health
echo "--- Health Checks ---"
curl -fsS "$BASE_URL/health" >/dev/null && pass "Health" || fail "Health"
curl -fsS "$BASE_URL/actuator/health/readiness" >/dev/null && pass "Readiness" || fail "Readiness"

# 2. Happy Path (generates metrics)
echo "--- Happy Path ---"
BALANCE=$(curl -fsS "$BASE_URL/api/account/balance?accountId=ACC123")
echo "$BALANCE" | grep -q '"balance":50000' && pass "Balance" || fail "Balance"

# 3. Prometheus Metrics (after traffic)
echo "--- Prometheus Metrics ---"
curl -fsS "$BASE_URL/actuator/prometheus" | grep -q '^gateway_requests_total' && pass "Metrics" || fail "Metrics"

# 4. Payment + Idempotency
echo "--- Payment + Idempotency ---"
PAY='{"fromAccountId":"ACC123","toAccountId":"ACC456","amount":500,"currency":"INR","idempotencyKey":"ci-payment-1"}'
PAY_RESP=$(curl -fsS -X POST "$BASE_URL/api/payments/transfer" \
  -H 'Content-Type: application/json' \
  -H 'Idempotency-Key: ci-payment-1' \
  -d "$PAY")
echo "$PAY_RESP" | grep -q '"status":"SUCCESS"' && pass "First Payment" || fail "First Payment"

curl -fsS -D replay.headers -o replay.json -X POST "$BASE_URL/api/payments/transfer" \
  -H 'Content-Type: application/json' \
  -H 'Idempotency-Key: ci-payment-1' \
  -d "$PAY"
grep -qi '^Idempotency-Replayed: true' replay.headers && pass "Replay" || fail "Replay"

# 5. Security Checks
echo "--- Security ---"
test "$(curl -sS -o /dev/null -w '%{http_code}' "$BASE_URL/api/account/balance?accountId=ACC123%27%20OR%20%271%27%3D%271")" = 403 && pass "SQLi 403" || fail "SQLi 403"
test "$(curl -sS -o /dev/null -w '%{http_code}' -X PROPFIND "$BASE_URL/api/account/balance?accountId=ACC123")" = 405 && pass "PROPFIND 405" || fail "PROPFIND 405"
test "$(curl -sS -o /dev/null -w '%{http_code}' -X POST "$BASE_URL/api/payments/transfer" -H 'Content-Type: application/json' -H 'Content-Encoding: gzip' -d 'not-gzip')" = 415 && pass "Gzip 415" || fail "Gzip 415"
test "$(curl -sS -o /dev/null -w '%{http_code}' "$BASE_URL/feedback/summary")" = 401 && pass "Feedback 401" || fail "Feedback 401"

# 6. Compliance Export
echo "--- Compliance ---"
curl -fsS "$BASE_URL/compliance/evidence/summary" -H "X-Internal-Ops-Key: $OPS_KEY" | grep -q '"attestation"' && pass "Summary" || fail "Summary"
curl -fsS "$BASE_URL/compliance/evidence/audit.csv?limit=10" -H "X-Internal-Ops-Key: $OPS_KEY" | grep -q '^id,timestamp,tenant_id' && pass "CSV" || fail "CSV"

echo ""
echo -e "${GREEN}🎉 ALL SMOKE TESTS PASSED${NC}"
exit 0