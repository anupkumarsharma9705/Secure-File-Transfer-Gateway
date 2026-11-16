#!/bin/bash
# Red Flag Audit Script — Run in each project root
# Exit codes: 0 = all clean, 1 = red flags found

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

echo "=== Red Flag Audit ==="

check "CI Green" "gh run list --limit 1 --json conclusion --jq '.[0].conclusion' | grep -q success"
check "No Secrets" "! trufflehog git file://. --only-verified --no-verification 2>/dev/null | grep -q ."
check "Live URL in README" "grep -q 'https://' README.md"
check "Load Results Exist" "[ -f load-tests/RESULTS.md ]"
check "No .env in History" "! git log --all --full-history -- .env 2>/dev/null | grep -q ."
check "≥ 3 ADRs" "[ -d docs/ADR ] && [ \$(ls docs/ADR | wc -l) -ge 3 ]"
check "Contract Tests" "grep -r 'pact\|Pact' pom.xml build.gradle package.json 2>/dev/null | grep -q ."
check "No Hardcoded Secrets" "! grep -r 'AKIA\|sk_live_\|password.*=' --include='*.java' --include='*.ts' --include='*.go' . 2>/dev/null | grep -v test | grep -q ."
check "≥ 5 Commits" "[ \$(git log --oneline | wc -l) -ge 5 ]"
check "Live URL Returns 200" "curl -fsS -o /dev/null -w '%{http_code}' \$(grep -oP 'https://[^ ]+' README.md | head -1) 2>/dev/null | grep -q 200"
check "Cross-Browser Tests" "[ -f package.json ] && grep -q 'playwright\|cypress' package.json"
check "Failure Matrix in Ops" "[ -f docs/OPERATIONS.md ] && grep -q 'Failure Matrix' docs/OPERATIONS.md"
check "No Tutorial Patterns" "! grep -r 'Thread.sleep\|ConcurrentHashMap.*idempotency\|RestTemplate\|System.out.println' --include='*.java' src/main 2>/dev/null | grep -q ."
check "≥ 3 ADRs for Key Decisions" "[ \$(ls docs/ADR/*.md 2>/dev/null | wc -l) -ge 3 ]"
check "No TODO/FIXME in Prod" "! grep -r 'TODO\|FIXME\|HACK\|XXX' --include='*.java' --include='*.ts' --include='*.go' src/main 2>/dev/null | grep -q ."
check "Customized README" "[ -f ../examples/README.template.md ] && ! diff -q README.md ../examples/README.template.md > /dev/null 2>&1"

echo ""
if [ $RED -eq 0 ]; then
  echo "🎉 ALL CLEAN"
else
  echo "⚠️  $RED RED FLAGS FOUND — FIX BEFORE APPLYING"
fi
exit $RED