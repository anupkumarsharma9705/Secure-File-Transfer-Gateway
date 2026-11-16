# Production-Ready Project Template

**Purpose:** A complete, copy-pasteable standard for turning any side project into a portfolio piece that passes a senior engineer's code review and gets you interviewed.

**Target:** 2025 grads, career switchers, junior devs with 0–2 years experience who need to prove they can *ship*, not just code.

---

## What "Production-Ready" Actually Means

| Level | Description | Your Goal |
|-------|-------------|-----------|
| **Tutorial** | `npm create`, follows a blog post, works on my machine | ❌ Baseline |
| **Polished** | Tests pass, README exists, deployed somewhere | ❌ Still junior |
| **Production-Ready** | Green CI, measurable load data, live URL, documented failure modes, operable by strangers | ✅ **This template** |
| **Product-Grade** | HA, pen-test, compliance, SLA, on-call runbook | Senior+ |

---

## The 3-Project Portfolio Strategy

| Project | Shape | Proves |
|---------|-------|--------|
| **1. Gateway / Infra** | API Gateway, rate limiting, auth, observability | You understand *systems* |
| **2. File-Sharing Webapp** | AuthZ, object storage, chunked upload, UI, orchestrator | You build *products* |
| **3. Different Shape** | CLI / Data Pipeline / Operator / Real-time | You're not a one-trick pony |

**Each must hit every checklist in this folder.**

---

## How to Use This Folder

```
production-ready-template/
├── 00-overview/           ← Start here
├── 01-repo-hygiene/       ← Git, license, ignore, editorconfig
├── 02-infra-deploy/       ← Docker, compose, deploy, health
├── 03-code-quality/       ← Static analysis, deps, secrets
├── 04-testing/            ← Unit, contract, integration, load
├── 05-documentation/      ← README, ARCH, OPS, ADRs
├── 06-cicd/               ← GitHub Actions, jobs, gates
├── 07-file-sharing-spec/  ← Concrete spec for project #2
├── 08-orchestrator-spec/  ← Saga/orchestrator for project #2
├── 09-exit-criteria/      ← Measurable "done" definitions
├── 10-portfolio/          ← GitHub profile, asciinema, resume
├── 11-checklists/         ← Per-project tick lists
├── 12-red-flags/          ← What kills credibility
└── examples/              ← Real configs from the gateway project
```

Read in order. Copy files from `examples/` into your projects. Tick every box in `11-checklists/`.

---

## Time Investment

| Project | Weeks (part-time) | Weeks (full-time) |
|---------|-------------------|-------------------|
| Gateway (done) | — | — |
| File-Sharing | 4–6 | 2–3 |
| Third (different shape) | 3–4 | 1.5–2 |
| **Total** | **7–10** | **3.5–5** |

**Worth it?** Yes. This is the difference between 200 applications → 0 calls and 20 applications → 8 calls.

---

## Prerequisites

- Docker + Compose v2
- GitHub account (for Actions, Packages, Pages)
- Free tier on Fly.io / Render / Railway / Netlify
- k6 installed (`winget install GrafanaLabs.k6` / `brew install k6`)
- IDE with Docker + GitHub Actions support

---

## First Step

```bash
# 1. Clone this template folder into your workspace
cp -r production-ready-template ~/dev/

# 2. Read 00-overview/README.md (this file)
# 3. Pick your next project (File-Sharing recommended)
# 4. Open 11-checklists/file-sharing-checklist.md
# 5. Start ticking boxes
```

---

## Support

- Each folder has a `README.md` + `CHECKLIST.md` + `examples/`
- Gateway project in `../fintech-gateway/` is the reference implementation
- When stuck: check `examples/` first, then the gateway source