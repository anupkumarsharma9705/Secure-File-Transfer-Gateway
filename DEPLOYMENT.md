# Deployment Guide — SFTG on a Single Linux VM

This is the recommended production setup: one Ubuntu VM running the full
Docker Compose stack, with Caddy providing automatic HTTPS.

```
Internet
   │  80/443 (TLS)
   ▼
Caddy ──► frontend (nginx, unprivileged, :8080)
              │  /api/*
              ▼
          backend (Spring Boot, :8000) ──► mysql (:3306, internal only)
```

Only Caddy is exposed publicly. The backend and MySQL ports are bound to
`127.0.0.1` and are not reachable from the internet. File bytes live in the
`uploads_data` volume; metadata lives in `mysql_data`.

---

## 1. Prerequisites

- Ubuntu 22.04 or 24.04, **2 vCPU / 2 GB RAM / 20 GB disk** minimum.
  (Malware scanning is off-box via VirusTotal, so there is no ClamAV memory cost.)
- A domain with an **A record** pointing at the VM's public IP (for HTTPS).
- Firewall/security group open on **22, 80, 443 only**.
  Do **not** open 3306, 8000, or 8080.
- A VirusTotal API key (free tier is fine), plus Brevo credentials if you
  use email OTP verification.

## 2. Install Docker

```bash
sudo apt-get update
sudo apt-get install -y ca-certificates curl
sudo install -m 0755 -d /etc/apt/keyrings
sudo curl -fsSL https://download.docker.com/linux/ubuntu/gpg \
  -o /etc/apt/keyrings/docker.asc
sudo chmod a+r /etc/apt/keyrings/docker.asc

echo "deb [arch=$(dpkg --print-architecture) signed-by=/etc/apt/keyrings/docker.asc] \
https://download.docker.com/linux/ubuntu $(. /etc/os-release && echo "$VERSION_CODENAME") stable" \
  | sudo tee /etc/apt/sources.list.d/docker.list > /dev/null

sudo apt-get update
sudo apt-get install -y docker-ce docker-ce-cli containerd.io \
  docker-buildx-plugin docker-compose-plugin

sudo usermod -aG docker "$USER"
newgrp docker
```

## 3. Clone the repository

```bash
git clone https://github.com/anupkumarsharma9705/Secure-File-Transfer-Gateway.git
cd Secure-File-Transfer-Gateway
```

## 4. Configure environment

```bash
cp .env.example .env
nano .env
```

Required values:

| Variable | Production value |
|---|---|
| `DB_USERNAME` | e.g. `sftg` (not `root`) |
| `DB_PASSWORD` | long random password |
| `JWT_SECRET` | output of `openssl rand -base64 64` |
| `DOMAIN` | `files.example.com` (the DNS name) |
| `FRONTEND_PORT` | `8081` so Caddy can own ports 80/443 |
| `CORS_ALLOWED_ORIGIN` | `https://files.example.com` |
| `VITE_API_URL` | `/api` (do not change for this stack) |
| `VIRUSTOTAL_API_KEY` | your VirusTotal key (blank = scans skipped) |
| `BREVO_API_KEY`, `MAIL_*` | email credentials |

Generate the JWT secret and DB password:

```bash
openssl rand -base64 64     # JWT_SECRET
openssl rand -base64 24     # DB_PASSWORD
```

> `.env` is git-ignored. Never commit it, paste it into an issue, or build it
> into an image.

## 5. Start the stack

```bash
docker compose --profile tls up -d --build --wait
```

The first build takes 2–4 minutes (Maven + npm + image pulls). MySQL
initializes on first boot; the backend waits for it to become healthy.

## 6. Verify

```bash
docker compose ps
docker compose logs --tail=50 caddy
docker compose logs --tail=50 backend

# API through the public TLS endpoint
curl -fsS https://files.example.com/api/stats/health          # -> OK

# Secure headers + SPA
curl -fsSI https://files.example.com | grep -Ei 'strict-transport|x-frame|content-security'

# Readiness includes database + VirusTotal configuration
curl -fsS http://127.0.0.1:8000/actuator/health/readiness
```

Certificate issuance requires DNS to be correct and ports 80/443 to be open.
Check `docker compose logs caddy` if HTTPS does not come up within a minute.

## 7. Deploy updates

```bash
cd Secure-File-Transfer-Gateway
git pull
docker compose --profile tls up -d --build --wait
```

To roll back, check out the previous release tag and run the same command.
Images are rebuilt locally; nothing is pulled from a mutable `latest` tag.

## 8. Backups

```bash
# Database (the mysql container already has MYSQL_USER / MYSQL_PASSWORD)
docker compose exec -T mysql sh -c \
  'exec mysqldump -u"$MYSQL_USER" -p"$MYSQL_PASSWORD" SFTG' > "sftg-$(date +%F).sql"

# Uploaded files (volume name = <compose project>_uploads_data)
docker volume ls | grep uploads_data
docker run --rm \
  -v secure-file-transfer-gateway_uploads_data:/data:ro \
  -v "$PWD":/backup \
  alpine tar czf "/backup/uploads-$(date +%F).tgz" -C /data .
```

Copy both files off the VM (S3, Backblaze, another server). Test a restore
before you need it.

## 9. Operations

See **[OPERATIONS.md](OPERATIONS.md)** for the service topology, health
probes, failure matrix, and scheduled jobs.

```bash
docker compose ps
docker compose logs -f backend
docker compose restart backend
docker compose down            # stop (volumes preserved)
docker compose down -v         # DANGER: deletes database + uploads
```

Log rotation is configured for every service (3 × 10 MB per container) so a
chatty service cannot fill the disk.

## 10. HTTP-only fallback (not for production)

If you do not have a domain yet, set `DOMAIN=:80` and `FRONTEND_PORT=8081`
in `.env`, then run the same command. The app will be served over plain HTTP.
JWTs and passwords would travel in clear text — use this only for a short
smoke test, then move to a domain + HTTPS.

## 11. Go-live checklist

- [ ] `DOMAIN` resolves to the VM; HTTPS certificate issued by Caddy.
- [ ] `docker compose ps` shows `healthy` for `mysql`, `sftg_backend`, and `sftg_frontend`.
- [ ] `https://DOMAIN/api/stats/health` returns `OK`.
- [ ] MySQL, backend, and frontend ports are **not** reachable from the internet.
- [ ] Strong `DB_PASSWORD` and `JWT_SECRET` set; `.env` is not committed.
- [ ] VirusTotal key set (readiness shows `virustotal: configured`).
- [ ] Brevo credentials verified with a real registration.
- [ ] Nightly database + uploads backup copied off the VM.
- [ ] Uptime monitor points at `https://DOMAIN/` and `https://DOMAIN/api/stats/health`.
- [ ] Previously exposed credentials rotated and git history purged
      (see `PRODUCTION_READY_MASTER_CHECKLIST.md`).

## 12. Platform notes

- **Render / Railway / Fly.io:** use the two Docker images
  `anupkumar9705/sftg-backend` and `anupkumar9705/sftg-frontend`. The frontend
  container listens on **8080**, not 80. If the services are on different
  domains, build the frontend with an absolute `VITE_API_URL` and set
  `CORS_ALLOWED_ORIGIN` to the frontend origin.
- **Managed MySQL:** set `DB_URL` to the provider's JDBC URL and do not start
  the `mysql` service. Keep `DB_USERNAME`/`DB_PASSWORD` in the platform's
  secret manager.
- **File storage:** uploads live in the `uploads_data` Docker volume
  (`FILE_UPLOAD_DIR=/app/uploads`). Back up that volume together with MySQL.
