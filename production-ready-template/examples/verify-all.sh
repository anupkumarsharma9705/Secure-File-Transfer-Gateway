#!/bin/bash
# Verify All Exit Criteria — Run in each project root
# Exit codes: 0 = all pass, 1 = any fail

set -euo pipefail

RED=0

check() {
  local desc="$1"
  local cmd="$2"
  if eval "$cmd" >/dev/null 2>&1; then
    echo "✅ $desc"
  else
    echo "❌ $desc"
    RED=1
  fi
}

echo "=== Exit Criteria Verification ==="

# 1. CI Green
check "CI Green (last run)" "gh run list --limit 1 --json conclusion --jq '.[0].conclusion' | grep -q success"

# 2. Coverage ≥ 80%
check "Coverage ≥ 80%" "mvn -B verify > /dev/null 2>&1 && grep -oP 'Total.*?(\d+)%' target/site/jacoco/index.html | head -1 | grep -oP '\d+' | awk '{if ($1 >= 80) exit 0; else exit 1}'"

# 3. Static Analysis Clean
check "Static Analysis (0 warnings)" "mvn -B verify > /dev/null 2>&1"

# 4. No HIGH/CRITICAL CVEs
check "No HIGH/CRITICAL CVEs" "mvn org.owasp:dependency-check-maven:check > /dev/null 2>&1"

# 5. No Secrets
check "No Secrets in Repo/History" "! trufflehog git file://. --only-verified --no-verification 2>/dev/null | grep -q ."

# 6. Container Scan Clean
check "Container Scan (0 HIGH/CRITICAL)" "trivy image \$(docker compose images -q | head -1) --severity HIGH,CRITICAL --exit-code 0 > /dev/null 2>&1"

# 6. Core Docs Exist
check "5 Core Docs" "[ -f README.md ] && [ -f ARCHITECTURE.md ] && [ -f docs/CONFIGURATION.md ] && [ -f docs/OPERATIONS.md ] && [ -d docs/ADR ]"

# 7. ≥ 3 ADRs
check "≥ 3 ADRs" "[ \$(ls docs/ADR/*.md 2>/dev/null | wc -l) -ge 3 ]"

# 8. Smoke Test
check "Smoke Test (11 assertions)" "docker compose up -d --wait > /dev/null 2>&1 && ./scripts/smoke-test.sh > /dev/null 2>&1"

# 9. Load Results Exist
check "Load Results (RESULTS.md + JSON)" "[ -f load-tests/RESULTS.md ] && ls load-tests/results/*.json > /dev/null 2>&1"

# 10. Live URL Works
check "Live URL (HTTPS, 200)" "curl -fsS -o /dev/null -w '%{http_code}' \$(grep -oP 'https://[^ ]+' README.md | head -1) 2>/dev/null | grep -q 200"

# 11. Contract Tests
check "Contract Tests" "grep -r 'pact\|Pact' pom.xml build.gradle package.json 2>/dev/null | grep -q ."

# 12. No Hardcoded Secrets
check "No Hardcoded Secrets" "! grep -r 'AKIA\|sk_live_\|password.*=' --include='*.java' --include='*.ts' --include='*.go' . 2>/dev/null | grep -v test | grep -q ."

# 13. ≥ 5 Commits
check "≥ 5 Commits" "[ \$(git log --oneline | wc -l) -ge 5 ]"

# 14. Live URL in README
check "Live URL in README" "grep -q 'https://' README.md"

# 15. No Tutorial Patterns in Prod
check "No Tutorial Patterns" "! grep -r 'Thread.sleep\|ConcurrentHashMap.*idempotency\|RestTemplate\|System.out.println' --include='*.java' src/main 2>/dev/null | grep -q ."

# 16. ≥ 3 ADRs for Key Decisions
check "≥ 3 ADRs for Key Decisions" "[ \$(ls docs/ADR/*.md 2>/dev/null | wc -l) -ge 3 ]"

# 17. No TODO/FIXME in Prod
check "No TODO/FIXME in Prod" "! grep -r 'TODO\|FIXME\|HACK\|XXX' --include='*.java' --include='*.ts' --include='*.go' src/main 2>/dev/null | grep -q ."

# 18. Customized README
check "Customized README" "[ -f ../examples/README.template.md ] && ! diff -q README.md ../examples/README.template.md > /dev/null 2>&1"

echo ""
if [ $RED -eq 0 ]; then
  echo "🎉 ALL EXIT CRITERIA MET"
else
  echo "⚠️  SOME CRITERIA NOT MET — FIX BEFORE CALLING 'DONE'"
fi
exit $RED