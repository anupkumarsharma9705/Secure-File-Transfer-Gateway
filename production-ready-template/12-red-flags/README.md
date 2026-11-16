# Red Flags That Kill Credibility

**If any of these exist in your repo, fix before showing anyone.**

---

## 1. "Tests Pass Locally" But CI Red

| Symptom | Fix |
|---------|-----|
| `mvn test` passes, GitHub Actions fails | Fix CI first; no merge until green |
| Flaky tests ignored | Quarantine or fix; never `@Disabled` |
| "Works on my machine" | Reproduce in clean Docker container |

---

## 2. No Load Data / Fake Numbers

| Anti-Pattern | Fix |
|--------------|-----|
| "100k req/s!" with no evidence | Run k6, commit `RESULTS.md` + raw JSON |
| Screenshots of Grafana only | Raw JSON + `RESULTS.md` table |
| No load test at all | Add k6 script, run in CI weekly |

---

## 3. Secrets in Repo

| Found | Action |
|-------|--------|
| `.env` committed | `git filter-branch` / BFG Repo-Cleaner → rotate all |
| AWS keys, DB passwords | Rotate immediately |
| `id_rsa`, `*.pem`, `*.jks` | Remove from history, regenerate |
| Hardcoded in code | Use Vault/Doppler/GitHub Environments |

**Check:** `trufflehog git file://. --only-verified` — must return 0.

---

## 4. One "Initial Commit" + 50 Files

| Problem | Fix |
|---------|-----|
| No history, no context | `git rebase -i --root` → split into logical commits |
| No merge commits | `git merge --no-ff` for features |
| "WIP" / "fix" commits in main | Squash before merge (`git merge --squash`) |

---

## 5. No Live URL

| Excuse | Reality |
|--------|---------|
| "Deploying is hard" | Fly.io/Render free tier = 5 min |
| "Need custom domain" | `project.yourname.dev` = $12/yr |
| "HTTPS is complex" | Fly.io/Render = automatic TLS |

**No live URL = not deployed.**

---

## 6. UI Only Works on Chrome

| Test | Tool |
|------|------|
| Firefox | Manual + CI |
| Safari (WebKit) | Playwright `webkit` |
| Mobile viewport | Chrome DevTools device toolbar |
| Keyboard only | Tab through entire flow |
| Screen reader | NVDA / VoiceOver |
| axe-core | `npm run test:a11y` in CI |

---

## 7. No Failure Mode Documentation

| Missing | Add to `docs/OPERATIONS.md` |
|---------|----------------------------|
| What happens when DB dies? | Readiness fails, traffic stops |
| What happens when Redis dies? | Rate limit 0, idempotency fails closed |
| What happens when IdP dies? | Cached JWKS works, unknown `kid` = 503 |
| How to rotate keys? | Step-by-step in OPERATIONS.md |
| How to rollback? | `docker compose down && git revert` |

---

## 8. Tutorial Code in Production Paths

| Pattern | Replace With |
|---------|--------------|
| `Thread.sleep()` in rate limit | Redis sorted-set sliding window |
| `ConcurrentHashMap` for idempotency | Redis `SET NX EX` + Lua CAS |
| `RestTemplate` | `WebClient` / `HttpClient` |
| `System.out.println()` | Structured JSON logs (Logstash encoder) |
| `@Autowired` everywhere | Constructor injection |
| `try-catch (Exception e) { log.error }` | Specific exceptions, error codes |

---

## 9. No Contract Tests

| Boundary | Required |
|----------|----------|
| Gateway → Backend | Pact / Spring Cloud Contract |
| Gateway → Auth | Pact |
| Metadata → Storage | Pact |
| Worker → MinIO | Pact (or Testcontainers MinIO) |

---

## 10. "I'll Fix It Later" in Code

| Comment | Action |
|---------|--------|
| `// TODO: handle this` | Do it now or create GitHub Issue with link |
| `// FIXME: hack` | Fix now |
| `// HACK: temporary` | Not temporary; fix or document in ADR |
| `// XXX:` | Same as FIXME |

---

## 11. No ADRs

| Decision | ADR Required |
|----------|--------------|
| Why this DB? | Yes |
| Why this message queue? | Yes |
| Why this auth strategy? | Yes |
| Why this orchestrator? | Yes |
| Why this UI framework? | Yes |

---

## 12. Copy-Paste README

| Template | Customized? |
|----------|-------------|
| Problem statement | Your words |
| Architecture diagram | Your Mermaid |
| Run commands | Your actual commands |
| Gaps section | Your honest gaps |

---

## Quick Audit Script

```bash
#!/bin/bash
# run in repo root

echo "=== Red Flag Audit ==="

# 1. CI Red?
gh run list --limit 1 --json conclusion --jq '.[0].conclusion' | grep -q success || echo "❌ CI not green"

# 2. Secrets?
trufflehog git file://. --only-verified --no-verification 2>/dev/null | grep -q . && echo "❌ Secrets found"

# 3. Live URL?
grep -q "https://.*\.dev\|https://.*\.com" README.md || echo "❌ No live URL in README"

# 4. Load Results?
[ -f load-tests/RESULTS.md ] || echo "❌ No RESULTS.md"

# 5. Secrets in History?
git log --all --full-history -- .env 2>/dev/null | grep -q . && echo "❌ .env in history"

# 6. ADRs?
[ -d docs/ADR ] && [ "$(ls docs/ADR | wc -l)" -ge 3 ] || echo "❌ < 3 ADRs"

# 7. Contract Tests?
grep -r "pact\|Pact\|contract" pom.xml build.gradle 2>/dev/null | grep -q . || echo "❌ No contract tests"

# 8. Secrets in Code?
grep -r "AKIA\|sk_live_\|password.*=.*[^$]" --include="*.java" --include="*.ts" --include="*.go" . 2>/dev/null | grep -v test | grep -q . && echo "❌ Hardcoded secrets"

# 9. Single Commit?
[ "$(git log --oneline | wc -l)" -lt 5 ] && echo "❌ Too few commits"

# 10. Live URL Works?
grep -oP 'https://[^ ]+' README.md | head -1 | xargs -I{} curl -fsS -o /dev/null -w "%{http_code}" {} 2>/dev/null | grep -q 200 || echo "❌ Live URL not 200"

echo "=== Audit Complete ==="
```