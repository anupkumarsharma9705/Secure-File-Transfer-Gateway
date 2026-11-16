#!/bin/bash
# Smoke Test — 11 Assertions Post `docker compose up`
# Run after: docker compose up --build -d --wait
# Exit code: 0 = all pass, 1 = any fail

set -euo pipefail

BASE_URL="${BASE_URL:-http://localhost}"
API_URL="${BASE_URL}:8000/api"
FRONTEND_URL="${BASE_URL}"

PASS=0
FAIL=0

check() {
    local desc="$1"
    local cmd="$2"
    echo -n "Checking: $desc ... "
    if eval "$cmd" >/dev/null 2>&1; then
        echo "✅ PASS"
        ((PASS++))
    else
        echo "❌ FAIL"
        ((FAIL++))
    fi
}

check_json() {
    local desc="$1"
    local url="$2"
    local jq_filter="$3"
    echo -n "Checking: $desc ... "
    if curl -fsS "$url" | jq -e "$jq_filter" >/dev/null 2>&1; then
        echo "✅ PASS"
        ((PASS++))
    else
        echo "❌ FAIL"
        ((FAIL++))
    fi
}

echo "=== Smoke Tests (11 Assertions) ==="
echo "API: $API_URL"
echo "Frontend: $FRONTEND_URL"
echo ""

# 1. Backend health endpoint
check "1. Backend health endpoint returns OK" \
    "curl -fsS $API_URL/stats/health | grep -q OK"

# 2. Swagger UI accessible
check "2. Swagger UI loads" \
    "curl -fsS $BASE_URL:8000/swagger-ui/index.html | grep -q Swagger"

# 3. OpenAPI spec available
check "3. OpenAPI JSON spec available" \
    "curl -fsS $BASE_URL:8000/v3/api-docs | jq -e '.openapi' >/dev/null"

# 4. Frontend serves HTML
check "4. Frontend serves index.html" \
    "curl -fsS $FRONTEND_URL | grep -q '<!DOCTYPE html>'"

# 5. Frontend assets load (CSS/JS)
check "5. Frontend assets load (Vite manifest)" \
    "curl -fsS $FRONTEND_URL | grep -q 'type=\"module\"'"

# 6. CORS headers present on preflight
check "6. CORS preflight works" \
    "curl -fsS -X OPTIONS -H 'Origin: http://localhost:5173' -H 'Access-Control-Request-Method: POST' -H 'Access-Control-Request-Headers: Content-Type' $API_URL/auth/register -I | grep -qi 'access-control-allow-origin'"

# 7. Rate limiting headers present
check "7. Rate limiting headers on auth endpoint" \
    "curl -fsS -I $API_URL/auth/login | grep -qi 'x-ratelimit'"

# 8. Security headers on frontend
check "8. Security headers on frontend (X-Frame-Options)" \
    "curl -fsS -I $FRONTEND_URL | grep -qi 'x-frame-options'"

# 9. Security headers on frontend (X-Content-Type-Options)
check "9. Security headers on frontend (nosniff)" \
    "curl -fsS -I $FRONTEND_URL | grep -qi 'x-content-type-options'"

# 10. Database connectivity (via actuator readiness)
check "10. Database connectivity (actuator readiness implies DB up)" \
    "curl -fsS $API_URL/actuator/health/readiness | jq -e '.status == \"UP\"' >/dev/null"

# 11. Backend container healthy (virus scanning is off-box via VirusTotal,
#     so there is no local scanner container to probe)
check "11. Backend container healthy" \
    "docker compose ps backend | grep -q healthy"

echo ""
echo "=== Results ==="
echo "Passed: $PASS"
echo "Failed: $FAIL"
echo ""

if [ $FAIL -eq 0 ]; then
    echo "🎉 ALL 11 SMOKE TESTS PASSED"
    exit 0
else
    echo "⚠️  $FAIL SMOKE TESTS FAILED"
    echo "=== Debug info ==="
    docker compose ps
    echo "=== Backend logs (last 30 lines) ==="
    docker compose logs --tail=30 backend
    echo "=== Frontend logs (last 20 lines) ==="
    docker compose logs --tail=20 frontend
    exit 1
fi