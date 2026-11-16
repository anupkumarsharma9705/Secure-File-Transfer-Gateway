# Changelog

All notable changes to this project are documented here.
Format follows [Keep a Changelog](https://keepachangelog.com/en/1.1.0/);
versioning follows [SemVer](https://semver.org/).

## [Unreleased]

### Added
- Spring Boot Actuator with liveness/readiness probes and Prometheus metrics
- Dependency health indicator (MySQL + VirusTotal configuration)
- Structured JSON logging (prod) with `X-Correlation-ID` propagation
- Unit test suite (JWT, auth, users, files, sharing, cleanup, rate limiting)
- Pact consumer contract tests (5 interactions)
- Testcontainers integration suite (auth, share links, health)
- GitHub Actions CI (build, tests, static analysis, image scan, smoke test, deploy)
- Scheduled k6 load-test workflow with results artifact
- ADRs 001–003 (token blacklist, virus scanning, MIME detection)
- `ARCHITECTURE.md`, `SECURITY.md`, `OPERATIONS.md`, `CONTRIBUTING.md`
- Dependabot (Maven, npm, Actions, Docker)
- Non-root containers, resource limits, restart policies
- nginx security headers (CSP, Referrer-Policy, Permissions-Policy)

### Changed
- Secrets removed from tracked configuration; all values sourced from environment
- Build targets Java 21 consistently (removed obsolete source/target 7)
- Compose health dependency between backend and `mysql`
- **Malware scanning moved from ClamAV to the VirusTotal v3 API** — no local
  scanner container, fits 1–2 GB free cloud instances; hash lookup first,
  upload only unknown files, non-blocking on outage/quota (ADR-002)

### Fixed
- `/api/files/download/{filename}` path variable now explicitly bound, fixing 500s on download
- Removed debug-only `TestController` and an unused duplicate `PublicLinkModal`
- Unhandled exceptions are logged instead of printed to stdout

### Security
- Rotated and purged previously committed credentials (history rewrite documented)
