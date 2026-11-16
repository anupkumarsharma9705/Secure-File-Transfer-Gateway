#!/bin/bash
# Red Flag Audit Script — Run this to check production readiness
# Usage: ./scripts/red-flag-audit.sh
# Exit code: 0 = all clean, 1 = red flags found

set -euo pipefail

RED=0
YELLOW=0

check() {
    local desc="$1"
    local cmd="$2"
    if eval "$cmd" >/dev/null 2>&1; then
        echo "✅ $desc"
    else
        echo "❌ $desc"
        ((RED++))
    fi
}

warn() {
    local desc="$1"
    local cmd="$2"
    if eval "$cmd" >/dev/null 2>&1; then
        echo "✅ $desc"
    else
        echo "⚠️  $desc (warning)"
        ((YELLOW++))
    fi
}

echo "=== Red Flag Audit ==="
echo ""

# 1. CI Green
check "CI Green (last run successful)" "gh run list --limit 1 --json conclusion --jq '.[0].conclusion' 2>/dev/null | grep -q success"

# 2. No Secrets
check "No Secrets in repo/history" "! trufflehog git file://. --only-verified --no-verification 2>/dev/null | grep -q ."

# 3. Live URL in README
check "Live URL in README" "grep -q 'https://' README.md"

# 4. Load Results Exist
check "Load Results Exist (load-tests/RESULTS.md)" "[ -f load-tests/RESULTS.md ]"

# 5. No .env in History
check "No .env in History" "! git log --all --full-history -- .env 2>/dev/null | grep -q ."

# 6. ≥ 3 ADRs
check "≥ 3 ADRs in docs/ADR/" "[ -d docs/ADR ] && [ \$(ls docs/ADR 2>/dev/null | wc -l) -ge 3 ]"

# 7. Contract Tests (Pact)
check "Contract Tests (Pact in pom.xml)" "grep -q 'pact' app/app/SFTG-backend/pom.xml"

# 8. No Hardcoded Secrets
check "No Hardcoded Secrets (AKIA, sk_live_, password= in source)" "! grep -r 'AKIA\|sk_live_\|password.*=' --include='*.java' --include='*.ts' --include='*.go' . 2>/dev/null | grep -v test | grep -v '.git' | grep -q ."

# 9. ≥ 5 Commits
check "≥ 5 Commits" "[ \$(git log --oneline | wc -l) -ge 5 ]"

# 10. Live URL Returns 200
LIVE_URL=$(grep -oP 'https://[^ ]+' README.md | head -1)
if [ -n "$LIVE_URL" ]; then
    check "Live URL Returns 200 ($LIVE_URL)" "curl -fsS -o /dev/null -w '%{http_code}' '$LIVE_URL' 2>/dev/null | grep -q 200"
else
    echo "❌ Live URL Returns 200 (no URL found in README)"
    ((RED++))
fi

# 11. Cross-Browser Tests
if [ -f app/app/SFTG-frontend/package.json ]; then
    check "Cross-Browser Tests (Playwright/Cypress in package.json)" "grep -q 'playwright\|cypress' app/app/SFTG-frontend/package.json"
else
    echo "⚠️  Cross-Browser Tests (no package.json found)"
    ((YELLOW++))
fi

# 12. Failure Matrix in Ops
check "Failure Matrix in OPERATIONS.md" "[ -f OPERATIONS.md ] && grep -q 'Failure Matrix' OPERATIONS.md"

# 13. No Tutorial Patterns (Java)
check "No Tutorial Patterns in src/main (Thread.sleep, RestTemplate, System.out.println)" "! grep -r 'Thread.sleep\|ConcurrentHashMap.*idempotency\|RestTemplate\|System.out.println' --include='*.java' app/app/SFTG-backend/src/main 2>/dev/null | grep -q ."

# 14. No Contract Tests (duplicate check)
check "Contract Tests Exist (duplicate check)" "grep -q 'pact' app/app/SFTG-backend/pom.xml"

# 15. No TODO/FIXME in Prod Code
check "No TODO/FIXME/HACK/XXX in Prod Code" "! grep -r 'TODO\|FIXME\|HACK\|XXX' --include='*.java' --include='*.ts' --include='*.go' app/app/SFTG-backend/src/main 2>/dev/null | grep -q ."

# 16. ≥ 3 ADRs for Key Decisions
check "≥ 3 ADRs for Key Decisions" "[ \$(ls docs/ADR/*.md 2>/dev/null | wc -l) -ge 3 ]"

# 17. Customized README (not copy-paste)
if [ -f ../examples/README.template.md ]; then
    check "Customized README (differs from template)" "! diff -q README.md ../examples/README.template.md"
else
    warn "Customized README (no template to compare)" "true"
fi

# 18. Not Single Commit
COMMIT_COUNT=$(git log --oneline | wc -l)
if [ "$COMMIT_COUNT" -ge 10 ]; then
    check "Sufficient Commit History ($COMMIT_COUNT commits)" "true"
else
    echo "❌ Sufficient Commit History ($COMMIT_COUNT commits, need ≥ 10)"
    ((RED++))
fi

# 19. Live URL in README (duplicate)
check "Live URL in README (duplicate)" "grep -q 'https://' README.md"

# 20. Tutorial Patterns in Prod (duplicate)
check "No Tutorial Patterns in Prod (duplicate)" "! grep -r 'Thread.sleep\|ConcurrentHashMap.*idempotency\|RestTemplate' --include='*.java' app/app/SFTG-backend/src/main 2>/dev/null | grep -q ."

echo ""
echo "=== Summary ==="
echo "Red Flags (Blockers): $RED"
echo "Warnings: $YELLOW"
echo ""

if [ $RED -eq 0 ]; then
    echo "🎉 ALL CLEAN — Ready for production!"
    exit 0
else
    echo "⚠️  $RED RED FLAGS FOUND — Fix before applying/deploying"
    exit 1
fi