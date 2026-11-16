# Production-Ready Project Template — Index

## Overview

This template defines the **exact standard** for a production-grade portfolio project. Every project in your portfolio must hit every checklist in this folder.

---

## Folder Structure

```
production-ready-template/
├── 00-overview/           # Start here — philosophy, strategy, time estimate
├── 01-repo-hygiene/       # Git, license, ignore, editorconfig, dependabot
├── 02-infra-deploy/       # Docker, compose, deploy, health endpoints
├── 03-code-quality/       # Static analysis, deps, secrets, observability
├── 04-testing/            # Unit, contract, integration, smoke, load
├── 05-documentation/      # README, ARCH, CONFIG, OPS, ADRs
├── 06-cicd/               # GitHub Actions: build → test → smoke → load → deploy
├── 07-file-sharing-spec/  # Concrete spec for Project #2
├── 08-orchestrator-spec/  # Saga pattern for async workflows
├── 09-exit-criteria/      # Measurable "done" definitions
├── 10-portfolio/          # GitHub profile, asciinema, resume, interview prep
├── 11-checklists/         # (Created as CHECKLIST.md in each folder)
├── 12-red-flags/          # What kills credibility + audit script
└── examples/              # Copy-paste templates from the gateway project
```

---

## How to Use

### For a New Project

```bash
# 1. Copy the template structure
cp -r production-ready-template ~/dev/my-new-project/template-ref

# 2. Read in order
cat ~/dev/my-new-project/template-ref/00-overview/README.md

# 3. Scaffold repo hygiene
cp ~/dev/my-new-project/template-ref/examples/LICENSE.proprietary LICENSE
cp ~/dev/my-new-project/template-ref/examples/.gitignore .gitignore
cp ~/dev/my-new-project/template-ref/examples/.editorconfig .editorconfig
mkdir -p .github/workflows docs/ADR load-tests/results scripts
cp ~/dev/my-new-project/template-ref/examples/dependabot.yml .github/dependabot.yml
cp ~/dev/my-new-project/template-ref/examples/README.template.md README.md
cp ~/dev/my-new-project/template-ref/examples/ARCHITECTURE.template.md ARCHITECTURE.md
cp ~/dev/my-new-project/template-ref/examples/SECURITY.template.md SECURITY.md
cp ~/dev/my-new-project/template-ref/examples/CONFIGURATION.template.md docs/CONFIGURATION.md
cp ~/dev/my-new-project/template-ref/examples/OPERATIONS.template.md docs/OPERATIONS.md
cp ~/dev/my-new-project/template-ref/examples/ADR-template.md docs/ADR/0001-initial-decision.md

# 4. Customize every template (replace [brackets] with your values)

# 5. Start ticking checklists in order:
#    01-repo-hygiene/CHECKLIST.md → 12-red-flags/CHECKLIST.md
```

### For an Existing Project

```bash
# 1. Run the red-flag audit
bash ~/dev/production-ready-template/12-red-flags/scripts/red-flag-audit.sh

# 2. Fix every ❌ (priority: Blocker → High → Medium → Low)

# 3. Run exit criteria verification
bash ~/dev/production-ready-template/examples/verify-all.sh

# 3. Fill gaps until all ✅
```

---

## The 3-Project Portfolio

| # | Project | Shape | Proves |
|---|---------|-------|--------|
| 1 | **Gateway** | Infra / Security | You understand systems |
| 2 | **FileShare** | Product / Full-stack | You build products |
| 3 | **Your Choice** | Different shape | You're not one-trick |

**Different Shape Options:**
- CLI tool (packaging, distro, semver, plugins)
- Data Pipeline (CDC → Kafka → ClickHouse, exactly-once)
- Kubernetes Operator (CRD, reconciliation, webhooks)
- Real-time Engine (WebSocket, presence, CRDT)

---

## Time Investment

| Project | Part-Time (nights/weekends) | Full-Time |
|---------|-----------------------------|-----------|
| Gateway | ✅ Done | ✅ Done |
| FileShare | 4–6 weeks | 2–3 weeks |
| Third | 3–4 weeks | 1.5–2 weeks |
| **Total** | **7–10 weeks** | **3.5–5 weeks** |

---

## Verification Commands (Run in Each Project)

```bash
# 1. Red-flag audit (must be 0)
bash ~/dev/production-ready-template/12-red-flags/scripts/red-flag-audit.sh

# 2. Exit criteria (must be 0)
bash ~/dev/production-ready-template/examples/verify-all.sh

# 3. Full CI locally
mvn -B clean verify
docker compose up --build -d --wait
./scripts/smoke-test.sh
k6 run load-tests/baseline-test.js
```

---

## Portfolio Checklist (Final)

- [ ] 3 projects at exit-criteria level
- [ ] 3 distinct architectural shapes
- [ ] GitHub Profile README with 3 project cards + live URLs
- [ ] 3 Pinned Repos (one-liner + live URL)
- [ ] 3 Asciinema demos (60s each)
- [ ] 3 Resume lines (copy-paste ready)
- [ ] 4 Verbal answers × 3 projects (arch, failure, scaling, trade-off)
- [ ] 20 Target Companies Listed
- [ ] LinkedIn Headline Updated

---

## Support

- Each folder has `README.md` + `CHECKLIST.md`
- `examples/` has copy-paste templates from the gateway project
- Gateway project in `../fintech-gateway/` is the reference implementation
- When stuck: check `examples/` first, then gateway source

---

## License

This template is provided as-is for your portfolio building. Adapt freely.