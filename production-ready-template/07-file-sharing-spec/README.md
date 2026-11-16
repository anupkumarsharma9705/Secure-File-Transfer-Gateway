# File-Sharing Webapp — Production-Ready Spec

**Project #2: AuthZ, Object Storage, Chunked Upload, UI, Orchestrator**

---

## Services (5 + Orchestrator)

| Service | Tech | Port | Responsibility |
|---------|------|------|----------------|
| `api-gateway` | Spring Boot 3 / Go | 8080 | AuthZ, rate limit, presigned URLs, routing |
| `metadata-api` | Spring Boot / Quarkus | 8081 | File metadata, tags, sharing, search, permissions |
| `upload-worker` | Spring Batch / Go | — | Chunk assemble, virus scan, thumbnail, checksum |
| `storage-adapter` | MinIO (S3 API) | 9000/9001 | Object storage, lifecycle, versioning |
| `notification-svc` | Spring Boot / Go | 8082 | Email/push/webhook notifications |
| `orchestrator` | Temporal / Camunda | 8083 | Saga: upload → scan → thumbnail → index → notify |

---

## API Gateway — Required Endpoints

| Method | Path | Auth | Description |
|--------|------|------|-------------|
| `POST` | `/api/v1/uploads/initiate` | Bearer | Start multipart upload, return `uploadId` + presigned URLs |
| `PUT` | `/api/v1/uploads/{uploadId}/parts/{partNumber}` | Presigned | Direct-to-MinIO part upload |
| `POST` | `/api/v1/uploads/{uploadId}/complete` | Bearer | Assemble → trigger orchestrator |
| `GET` | `/api/v1/files` | Bearer | List (paginated, search, filter) |
| `GET` | `/api/v1/files/{id}` | Bearer | Metadata + presigned download URL |
| `DELETE` | `/api/v1/files/{id}` | Bearer | Soft delete + revoke URLs |
| `POST` | `/api/v1/files/{id}/share` | Bearer | Create sharing link (expiry, password) |
| `GET` | `/api/v1/shares/{token}` | Public | Access shared file |
| `GET` | `/health` | None | Liveness |
| `GET` | `/actuator/health/readiness` | None | Readiness |

---

## Metadata API — Data Model

```sql
CREATE TABLE files (
  id              UUID PRIMARY KEY,
  owner_id        UUID NOT NULL,
  original_name   TEXT NOT NULL,
  stored_name     TEXT NOT NULL,      -- MinIO object key
  bucket          TEXT NOT NULL,
  size_bytes      BIGINT NOT NULL,
  mime_type       TEXT NOT NULL,
  checksum_sha256 CHAR(64) NOT NULL,
  status          TEXT NOT NULL,      -- UPLOADING, READY, ARCHIVED, DELETED
  tags            JSONB DEFAULT '[]',
  created_at      TIMESTAMPTZ DEFAULT now(),
  updated_at      TIMESTAMPTZ DEFAULT now(),
  deleted_at      TIMESTAMPTZ
);

CREATE TABLE file_versions (
  id           UUID PRIMARY KEY,
  file_id      UUID REFERENCES files(id),
  version      INT NOT NULL,
  size_bytes   BIGINT NOT NULL,
  checksum     CHAR(64) NOT NULL,
  created_at   TIMESTAMPTZ DEFAULT now()
);

CREATE TABLE shares (
  id            UUID PRIMARY KEY,
  file_id       UUID REFERENCES files(id),
  token         CHAR(32) UNIQUE NOT NULL,
  password_hash TEXT,                    -- bcrypt if set
  expires_at    TIMESTAMPTZ,
  max_downloads INT,
  download_count INT DEFAULT 0,
  created_by    UUID NOT NULL,
  created_at    TIMESTAMPTZ DEFAULT now()
);
```

---

## Upload Worker — Chunked Upload Flow

```
Client                          Gateway                    MinIO              Worker
  │                                │                          │                  │
  ├── POST /uploads/initiate ────►│                          │                  │
  │◄──── {uploadId, presignedURLs}──│                          │                  │
  │                                │                          │                  │
  ├── PUT /parts/1 (direct) ──────►│                          │                  │
  ├── PUT /parts/2 (direct) ──────►│                          │                  │
  ...                                │                          │                  │
  ├── POST /uploads/{id}/complete──►│                          │                  │
  │                                ├── Orchestrator: start ──►│                  │
  │                                │                          │                  │
  │                                │                    (parts stored)          │
  │                                │                          │                  │
  │◄──── 202 Accepted ─────────────│                          │                  │
  │                                │                          │                  │
  │                                │                    ┌────►│ Assemble parts   │
  │                                │                    │     │ Virus scan       │
  │                                │                    │     │ Thumbnail gen    │
  │                                │                    │     │ Checksum verify  │
  │                                │                    │     │ Index metadata   │
  │                                │                    │     │ Notify           │
  │                                │                    ▼     ▼                  │
```

**Worker Steps (Idempotent, Keyed by `uploadId`):**
1. **Assemble** → Download all parts → Single object in MinIO
2. **Scan** → ClamAV / `go-clamd` → Quarantine on hit
3. **Thumbnail** → `libvips` / `sharp` for images, `ffmpeg` for video
4. **Checksum** → SHA-256 verify against client-provided
5. **Index** → Call Metadata API → `status = READY`
6. **Notify** → Webhook / Email / Push

---

## Orchestrator — Saga Definition

```yaml
# Temporal / Camunda / Custom
workflow: ProcessUpload
input: { uploadId, fileId, ownerId, partsCount }

steps:
  - name: AssembleParts
    service: upload-worker
    compensation: DeleteAssembledObject
    retry: 3, exponential

  - name: VirusScan
    service: upload-worker
    compensation: QuarantineObject
    retry: 2

  - name: GenerateThumbnails
    service: upload-worker
    compensation: DeleteThumbnails
    retry: 3

  - name: VerifyChecksum
    service: upload-worker
    compensation: MarkCorrupted
    retry: 1

  - name: IndexMetadata
    service: metadata-api
    compensation: SoftDeleteFile
    retry: 3

  - name: NotifyRecipients
    service: notification-svc
    compensation: LogNotificationFailure
    retry: 5
```

**Visibility Dashboard:** Running / Completed / Failed / Compensating, latency p95, retry count.

---

## UI — Required Pages (React + Vite + Tailwind + shadcn-ui)

| Page | Components | Accessibility |
|------|------------|---------------|
| **Landing** | Hero, features, auth buttons, live demo link | WCAG 2.1 AA |
| **Login/Register** | OAuth2 (Google/GitHub), Magic Link, Email/Pass | ARIA labels, focus mgmt |
| **Dashboard** | Virtualized list (react-window), drag-drop (dnd-kit), progress bars, share modal | Keyboard nav, screen reader |
| **File View** | Preview (PDF.js, `<video>`, `<img>`), version history table, permission matrix | Alt text, captions |
| **Settings** | API keys, webhooks, storage quota gauge, team invite | Form validation, announcements |
| **Share Modal** | Expiry picker, password toggle, copy link, QR code | Focus trap, ESC close |

**Dark Mode:** System preference + manual toggle, no flash (CSS `color-scheme`).

---

## Security Requirements

| Control | Implementation |
|---------|----------------|
| Auth | OAuth2 (Google/GitHub) + JWT + Magic Link (email) |
| AuthZ | Owner-only by default; shares via signed tokens |
| Rate Limit | 50 req/s upload init, 200 req/s part upload |
| Presigned URLs | 15-min TTL, single-use for parts |
| Virus Scan | ClamAV on every upload, quarantine on hit |
| Encryption | TLS 1.3 everywhere, MinIO SSE-S3 at rest |
| Audit | Every file op → MySQL audit log |
| CORS | Strict origins, no credentials on public share |

---

## Observability

| Metric | Name | Labels |
|--------|------|--------|
| Upload throughput | `files_upload_total` | `status` (success/failed/quarantined) |
| Upload latency | `files_upload_duration_seconds` | `stage` (assemble/scan/thumbnail/index) |
| Part upload rate | `files_parts_uploaded_total` | `bucket` |
| Share downloads | `files_share_download_total` | `share_type` (public/password) |
| Worker queue depth | `worker_queue_depth` | `stage` |
| Saga duration | `orchestrator_saga_duration_seconds` | `workflow`, `status` |

---

## CI/CD Additions

| Addition | File |
|----------|------|
| UI Tests | `ui-tests/` (Playwright, axe-core) |
| Visual Regression | `ui-tests/visual/` (Chromatic/Percy) |
| E2E Upload | `tests/e2e/upload.spec.ts` |
| Accessibility | `npm run test:a11y` (axe-core) |

---

## Exit Criteria (File-Sharing)

| Metric | Target |
|--------|--------|
| `docker compose up` healthy | < 120s |
| Unit + Contract + Integration | ≥ 150 tests, 0 failures |
| Smoke test | 15 assertions (incl. upload, share, download) |
| Load: upload init | 100 req/s, p95 < 100ms |
| Load: part upload (direct MinIO) | 500 req/s, p95 < 50ms |
| Load: share download | 200 req/s, p95 < 80ms |
| Virus scan throughput | 50 files/min |
| UI accessibility | axe-core 0 violations |
| Deployed URL | Custom domain, HTTPS, status page |