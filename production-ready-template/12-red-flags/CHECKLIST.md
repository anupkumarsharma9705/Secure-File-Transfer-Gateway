# Red Flags Checklist

**Run `./scripts/red-flag-audit.sh` in each repo. All must be ✅.**

| # | Red Flag | Check Command | Status |
|---|----------|---------------|--------|
| 1 | CI not green | `gh run list --limit 1 --json conclusion` | ☐ |
| 2 | Secrets in repo/history | `trufflehog git file://. --only-verified` | ☐ |
| 3 | No live URL in README | `grep -q "https://" README.md` | ☐ |
| 4 | No load results | `[ -f load-tests/RESULTS.md ]` | ☐ |
| 5 | .env in history | `git log --all --full-history -- .env` | ☐ |
| 6 | < 3 ADRs | `[ -d docs/ADR ] && ls docs/ADR | wc -l` | ☐ |
| 7 | No contract tests | `grep -r pact pom.xml build.gradle` | ☐ |
| 8 | Hardcoded secrets | `grep -r "AKIA\|sk_live_\|password.*=" --include="*.java" --include="*.ts"` | ☐ |
| 9 | Too few commits | `git log --oneline | wc -l` | ☐ |
| 10 | Live URL not 200 | `curl -fsS -o /dev/null -w "%{http_code}" $(grep -oP 'https://[^ ]+' README.md | head -1)` | ☐ |
| 11 | UI only Chrome | `playwright test --project=firefox,webkit` | ☐ |
| 12 | No failure docs | `[ -f docs/OPERATIONS.md ] && grep -q "Failure Matrix" docs/OPERATIONS.md` | ☐ |
| 13 | Tutorial patterns | `grep -r "Thread.sleep\|ConcurrentHashMap.*idempotency\|RestTemplate\|System.out.println" --include="*.java"` | ☐ |
| 14 | No contract tests | `grep -r "pact\|Pact" pom.xml build.gradle package.json` | ☐ |
| 15 | "TODO/FIXME" in prod code | `grep -r "TODO\|FIXME\|HACK\|XXX" --include="*.java" --include="*.ts" --include="*.go" | grep -v test` | ☐ |
| 16 | No ADRs for key decisions | `ls docs/ADR/*.md | wc -l` | ☐ |
| 17 | Copy-paste README | `diff README.md ../examples/README.template.md` | ☐ |
| 18 | Single commit | `git log --oneline | wc -l` | ☐ |
| 19 | No live URL in README | `grep -q "https://" README.md` | ☐ |
| 20 | Tutorial patterns in prod | `grep -r "Thread.sleep\|ConcurrentHashMap\|RestTemplate" --include="*.java" src/main` | ☐ |

---

## Fix Priority

| Priority | Flags | Timeline |
|----------|-------|----------|
| **Blocker** | 1, 2, 3, 4 | Fix before any application |
| **High** | 5, 6, 7, 8 | Fix this week |
| **Medium** | 9, 10, 11, 12 | Fix before applications |
| **Low** | 13, 14, 15, 16, 17, 18, 19, 20 | Fix during polish |

---

## Verification Script

Create `scripts/red-flag-audit.sh`:

```bash
#!/bin/bash
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
check "Customized README" "[ -f ../examples/README.template.md ] && ! diff -q README.md ../examples/README.template.md"

echo ""
if [ $RED -eq 0 ]; then
  echo "🎉 ALL CLEAN"
else
  echo "⚠️  $RED RED FLAGS FOUND — FIX BEFORE APPLYING"
fi
exit $RED
```