# Repo Hygiene Checklist

**Every repository must have these before first commit.**

---

## Files Required at Root

| File | Purpose | Template Location |
|------|---------|-------------------|
| `LICENSE` | Legal; proprietary or deliberate OSS | `examples/LICENSE.proprietary` |
| `.gitignore` | Excludes build, secrets, IDE, OS junk | `examples/.gitignore` |
| `.editorconfig` | Indent, charset, line endings | `examples/.editorconfig` |
| `.github/dependabot.yml` | Auto dependency updates | `examples/dependabot.yml` |
| `README.md` | Problem → Arch → Run → Test → Deploy → Gaps | `examples/README.template.md` |
| `ARCHITECTURE.md` | Mermaid diagram + component descriptions | `examples/ARCHITECTURE.template.md` |
| `SECURITY.md` | Vulnerability reporting, scope, deployment | `examples/SECURITY.template.md` |

---

## Directory Structure (Standard)

```
repo-root/
├── .github/
│   ├── workflows/
│   │   └── ci.yml
│   └── dependabot.yml
├── docs/
│   ├── CONFIGURATION.md
│   ├── OPERATIONS.md
│   └── ADR/
│       └── 0001-<decision>.md
├── src/ or <service>/
├── load-tests/
│   ├── *.js
│   ├── RESULTS.md
│   └── results/
├── docker-compose.yml
├── .env.example
├── Dockerfile
├── LICENSE
├── .gitignore
├── .editorconfig
├── README.md
├── ARCHITECTURE.md
├── SECURITY.md
└── pom.xml / build.gradle / package.json / go.mod
```

---

## `.gitignore` — Minimum Entries

```gitignore
# Build
target/
build/
dist/
*.class
*.jar
*.war

# IDE
.idea/
*.iml
.vscode/
*.swp

# Secrets & Local
.env
.env.*
!.env.example
*.pem
*.key
*.jks
secrets/

# Logs
logs/
*.log

# OS
.DS_Store
Thumbs.db

# AI/Scratch
.aider*
File Listing
```

---

## `.editorconfig` — Standard

```ini
root = true

[*]
indent_style = space
indent_size = 2
end_of_line = lf
charset = utf-8
trim_trailing_whitespace = true
insert_final_newline = true

[*.{java,kt,cs}]
indent_size = 4

[*.{md,yml,yaml,json,jsonc}]
indent_size = 2

[Dockerfile]
indent_size = 2
```

---

## `dependabot.yml` — Weekly Updates

```yaml
version: 2
updates:
  - package-ecosystem: "maven"
    directory: "/"
    schedule:
      interval: "weekly"
      day: "monday"
      time: "06:00"
    open-pull-requests-limit: 10
    labels: ["dependencies", "maven"]
  - package-ecosystem: "npm"
    directory: "/"
    schedule:
      interval: "weekly"
    labels: ["dependencies", "npm"]
  - package-ecosystem: "github-actions"
    directory: "/"
    schedule:
      interval: "weekly"
    labels: ["dependencies", "github-actions"]
```

---

## Git Commit Standards

| Rule | Example |
|------|---------|
| **Conventional Commits** | `feat: add idempotency key validation` |
| **One logical change per commit** | No "fix + refactor + test" in one |
| **Message body explains *why*** | Not just "fixed bug" |
| **Merge commits for history preservation** | `git merge --no-ff` |
| **No "WIP" / "fix" / "update" commits in main** | Squash before merge |

---

## Branch Strategy

| Branch | Purpose |
|--------|---------|
| `main` | Always deployable, green CI |
| `feature/*` | One ticket, rebased on main |
| `release/v*` | Tagged releases only |
| `hotfix/*` | Production fixes only |

---

## Quick Setup Script

```bash
#!/bin/bash
# run once per new repo

cp ../production-ready-template/examples/LICENSE.proprietary LICENSE
cp ../production-ready-template/examples/.gitignore .gitignore
cp ../production-ready-template/examples/.editorconfig .editorconfig
mkdir -p .github/workflows docs/ADR load-tests/results
cp ../production-ready-template/examples/dependabot.yml .github/dependabot.yml
cp ../production-ready-template/examples/README.template.md README.md
cp ../production-ready-template/examples/ARCHITECTURE.template.md ARCHITECTURE.md
cp ../production-ready-template/examples/SECURITY.template.md SECURITY.md

echo "Repo hygiene scaffolded. Fill in the templates."
```