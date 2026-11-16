# Contributing

## Getting Started

```bash
cp .env.example .env                      # fill in local values
docker compose up mysql_db                # dependencies only
cd app/app/SFTG-backend && ./mvnw spring-boot:run
cd app/app/SFTG-frontend && npm install && npm run dev
```

## Before Opening a PR

```bash
# Backend
cd app/app/SFTG-backend
./mvnw -B verify                          # tests + coverage report
./mvnw -B spotbugs:check pmd:check
./mvnw -B org.owasp:dependency-check-maven:check

# Frontend
cd app/app/SFTG-frontend
npm run lint && npm run build

# Full stack
docker compose up --build -d --wait
./scripts/smoke-test.sh
./scripts/red-flag-audit.sh
```

## Rules

- Never commit secrets. Use environment variables; `.env` is ignored.
- One logical change per PR; conventional commit messages (`feat:`, `fix:`, `chore:`).
- New endpoint ⇒ controller test + contract update if the frontend consumes it.
- New external dependency ⇒ document the failure mode in `OPERATIONS.md`.
- Architecture decisions go in `docs/ADR/` with context, decision, consequences.
