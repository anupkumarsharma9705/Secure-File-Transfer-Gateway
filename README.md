# 🔐 SFTG — Secure File Transfer Gateway

A production-grade, full-stack secure file sharing platform built with **Java Spring Boot** and **React**. Designed with Fintech-level security standards including JWT authentication, token blacklisting, virus scanning, audit logging, and expiring share links.

> **Swagger UI:** [https://sftg-backend.onrender.com/swagger-ui/index.html](https://sftg-backend.onrender.com/swagger-ui/index.html)
> 
---

## 📋 Table of Contents

- [Features](#-features)
- [Tech Stack](#-tech-stack)
- [Architecture](#-architecture)
- [Security Design](#-security-design)
- [API Endpoints](#-api-endpoints)
- [Database Schema](#-database-schema)
- [Getting Started](#-getting-started)
- [Environment Variables](#-environment-variables)
- [Docker Deployment](#-docker-deployment)
- [Cloud Deployment](#-cloud-deployment)
- [Project Structure](#-project-structure)
- [Key Engineering Decisions](#-key-engineering-decisions)
- [Author](#-author)

---

## ✨ Features

### Security
- 🔐 **JWT Authentication** with DB-backed token blacklisting (survives server restarts)
- 📧 **Email OTP Verification** via Brevo API before account activation
- 🔒 **BCrypt Password Hashing** with secure salting
- 🛡️ **Rate Limiting** on public endpoints using Bucket4j token bucket algorithm
- 🦠 **Virus Scanning** — ClamAV (local Docker) / VirusTotal API (cloud)
- 🔍 **MIME Type Detection** via Apache Tika (content-based, not extension-based)
- 🚫 **Executable File Blocking** — rejects .exe, .sh, .bat uploads
- 🛡️ **Path Traversal Protection** on all file operations

### File Management
- 📁 **Secure File Upload** with size validation (max 100MB)
- ⏳ **Auto File Expiry** — files automatically deleted after 1 hour
- 🗑️ **Scheduled Cleanup** — removes expired files and all associated data every 5 minutes
- ☁️ **Cloud Storage** via Cloudinary — stateless, no local disk dependency

### Sharing
- 🔗 **Expiring Share Links** — UUID tokens, expire in 1 hour
- 📊 **Download Limits** — maximum 5 downloads per share link
- 📋 **Download Audit** — tracks who downloaded, when, from which IP
- 📱 **QR Code Generation** for share links

### Monitoring & Audit
- 📊 **Fintech-Style Audit Log** — every action logged with IP address and User-Agent
- 📈 **Global Statistics** — total users, uploads, downloads
- 👤 **User Profile Dashboard** — complete activity history
- 🔄 **Keep-Alive Scheduler** — prevents cloud cold starts

---

## 🛠 Tech Stack

### Backend
| Technology | Version | Purpose |
|---|---|---|
| Java | 21 | Core language |
| Spring Boot | 3.4.11 | Application framework |
| Spring Security | 6.4.x | Authentication & authorization |
| JJWT | 0.11.5 | JWT token generation & validation |
| Hibernate / JPA | 6.6.x | Database ORM |
| MySQL | 8.0 | Primary database |
| Bucket4j | 8.15.0 | Rate limiting |
| Apache Tika | 2.8.0 | MIME type detection |
| Cloudinary SDK | 1.37.0 | Cloud file storage |
| OkHttp | 4.9.3 | Brevo email API client |
| SpringDoc OpenAPI | 2.8.5 | Swagger documentation |
| Maven | 3.x | Build & dependency management |

### Frontend
| Technology | Version | Purpose |
|---|---|---|
| React | 19 | UI framework |
| Vite | 8.x | Build tool |
| Material UI (MUI) | 7.x | Component library |
| React Router DOM | 7.x | Client-side routing |
| Axios | 1.x | HTTP client with interceptors |
| Tailwind CSS | 4.x | Utility CSS |
| qrcode.react | 4.x | QR code generation |

### Infrastructure
| Service | Purpose |
|---|---|
| Render | Backend hosting (Docker) |
| Railway | Frontend hosting (Docker) + MySQL database + ClamAV |
| Cloudinary | Cloud file storage |
| Docker Hub | Container image registry |
| UptimeRobot | Keep-alive monitoring |
| Brevo | Transactional email (OTP) |

---

## 🏗 Architecture

```
┌─────────────────────────────────────────────────────────┐
│                    CLIENT (Browser)                      │
│              React + MUI + Vite (Railway)                │
└──────────────────────────┬──────────────────────────────┘
                           │ HTTPS
                           ▼
┌─────────────────────────────────────────────────────────┐
│                   SPRING BOOT API                        │
│                   (Render - Docker)                      │
│                                                          │
│  ┌─────────────┐  ┌──────────────┐  ┌───────────────┐  │
│  │  Security   │  │  Controllers  │  │   Schedulers  │  │
│  │  JWT Filter │  │  Auth/File/  │  │  Cleanup      │  │
│  │  Rate Limit │  │  Share/Stats │  │  KeepAlive    │  │
│  └─────────────┘  └──────────────┘  └───────────────┘  │
│                           │                              │
│  ┌────────────────────────▼─────────────────────────┐   │
│  │                   Services                        │   │
│  │  FileService │ AuthService │ FileShareService     │   │
│  │  VirusTotal  │ Cloudinary  │ TokenBlacklist       │   │
│  └────────────────────────┬─────────────────────────┘   │
│                           │                              │
└──────────────────────────┬──────────────────────────────┘
                           │
          ┌────────────────┼────────────────┐
          ▼                ▼                ▼
   ┌─────────────┐  ┌───────────┐  ┌──────────────┐
   │   MySQL DB  │  │Cloudinary │  │  VirusTotal  │
   │  (Railway)  │  │  Storage  │  │     API      │
   └─────────────┘  └───────────┘  └──────────────┘
```

### Layered Architecture
```
Controller Layer   →  Handles HTTP, validates input, returns responses
Service Layer      →  Business logic, orchestration
Repository Layer   →  Database operations via JPA
Security Layer     →  JWT filter, rate limiting, CORS
```

---

## 🔐 Security Design

### Authentication Flow
```
Register → OTP Email → Verify → Login → JWT Token → Authenticated Requests
```

### Token Blacklisting — Why DB-Backed?
Most tutorials use in-memory maps for token blacklisting. This project uses database storage because:

```
In-memory approach problem:
  Server restarts → blacklisted tokens become valid again
  Attacker with stolen token just waits for deployment

DB-backed solution:
  Token stored in blacklisted_tokens table on logout
  Every request: SELECT exists WHERE token = ?
  Index on token column → O(log n) lookup
  Hourly scheduler: DELETE WHERE expires_at < NOW()
  Tokens remain blacklisted across restarts
```

### Security Layers
```
1. BCrypt (cost factor 10)      — password hashing
2. Email OTP verification        — prevents fake registrations
3. JWT (HS256, 2hr expiry)      — stateless authentication
4. Token blacklist (DB-backed)   — logout invalidation
5. Rate limiting (Bucket4j)      — 5 req/min on share endpoints
6. MIME type detection (Tika)    — content-based file validation
7. Executable blocking           — rejects dangerous file types
8. Path traversal protection     — filename sanitization
9. Virus scanning (VirusTotal)   — malware detection
10. Ownership verification        — users only access their own files
11. CORS configuration           — locked to specific frontend origin
```

---

## 📡 API Endpoints

### Authentication (Public)
```
POST   /api/auth/register       Register with email + password
POST   /api/auth/verify-otp     Verify 6-digit OTP
POST   /api/auth/login          Login, returns JWT token
POST   /api/auth/logout         Blacklist current token (auth required)
```

### Account (JWT Required)
```
GET    /api/account/me          Profile + full audit log
```

### Files (JWT Required)
```
POST   /api/files/upload               Upload file (multipart/form-data)
GET    /api/files/download/{filename}  Download own file
GET    /api/files/my-files             List all uploaded files with details
```

### Sharing
```
POST   /api/share/generate      Generate share link (JWT required)
GET    /api/share/download/{token}  Download via share link (public)
```

### Statistics (JWT Required)
```
GET    /api/stats/global        Total users, uploads, downloads
GET    /api/stats/health        Health check endpoint
```

### Documentation
```
GET    /swagger-ui/index.html   Interactive API documentation
GET    /v3/api-docs             OpenAPI JSON specification
```

---

## 🗄 Database Schema

```sql
users                    — credentials, OTP, timestamps
user_roles               — role assignments (ROLE_USER, ROLE_ADMIN)
files                    — metadata, Cloudinary URL, expiry
shared_links             — tokens, download limits, expiry
download_logs            — who downloaded, when, IP address
user_activity_logs       — full audit trail (LOGIN, LOGOUT, UPLOAD, DOWNLOAD)
blacklisted_tokens       — DB-backed JWT blacklist with cleanup
```

---

## 🚀 Getting Started

### Prerequisites
```
Java 21+
Maven 3.x
Node.js 20+
Docker + Docker Compose
```

### Option A — Local Development (Recommended)

**1. Clone the repository**
```bash
git clone https://github.com/anupkumar9705/SFTG.git
cd SFTG
```

**2. Start dependencies (MySQL + ClamAV)**
```bash
docker-compose up mysql_db clamav_service
```

**3. Configure backend**

Create `src/main/resources/application-dev.properties`:
```properties
spring.datasource.url=jdbc:mysql://127.0.0.1:3306/SFTG?useSSL=false&allowPublicKeyRetrieval=true&serverTimezone=UTC
spring.datasource.username=your_mysql_user
spring.datasource.password=your_mysql_password

jwt.secret=your_64_byte_base64_secret
jwt.expirationMs=7200000

clamav.host=127.0.0.1
clamav.port=3310

spring.mail.host=smtp-relay.brevo.com
spring.mail.port=587
spring.mail.username=your_brevo_smtp_user
spring.mail.password=your_brevo_smtp_password

brevo.api.key=your_brevo_api_key

cloudinary.cloud_name=your_cloud_name
cloudinary.api_key=your_cloudinary_api_key
cloudinary.api_secret=your_cloudinary_api_secret

virustotal.api.key=your_virustotal_api_key

cors.allowed-origin=http://localhost:5173
```

**4. Start backend**
```bash
cd app/backendJava/SFTG-backend
mvn spring-boot:run
```

**5. Start frontend**
```bash
cd app/backendJava/SFTG-frontend
npm install
npm run dev
```

**Access:**
- Frontend: http://localhost:5173
- Backend API: http://localhost:8000
- Swagger UI: http://localhost:8000/swagger-ui/index.html

### Option B — Full Docker

```bash
docker-compose up --build
```

- Frontend: http://localhost
- Backend: http://localhost:8000

---

## ⚙️ Environment Variables

### Backend
| Variable | Description | Example |
|---|---|---|
| `SPRING_PROFILES_ACTIVE` | Active profile | `dev` |
| `DB_URL` | JDBC MySQL URL | `jdbc:mysql://host:port/SFTG?...` |
| `DB_USERNAME` | Database username | `root` |
| `DB_PASSWORD` | Database password | `strongpassword` |
| `JWT_SECRET` | 64-byte base64 secret | `openssl rand -base64 64` |
| `JWT_EXPIRATION_MS` | Token expiry in ms | `7200000` |
| `CLAMAV_HOST` | ClamAV hostname | `127.0.0.1` |
| `CLAMAV_PORT` | ClamAV port | `3310` |
| `MAIL_HOST` | SMTP host | `smtp-relay.brevo.com` |
| `MAIL_PORT` | SMTP port | `587` |
| `MAIL_USERNAME` | SMTP username | `brevo_user` |
| `MAIL_PASSWORD` | SMTP password | `brevo_smtp_key` |
| `BREVO_API_KEY` | Brevo REST API key | `xkeysib-...` |
| `CLOUDINARY_CLOUD_NAME` | Cloudinary cloud name | `myapp` |
| `CLOUDINARY_API_KEY` | Cloudinary API key | `123456789` |
| `CLOUDINARY_API_SECRET` | Cloudinary secret | `abcdef...` |
| `VIRUSTOTAL_API_KEY` | VirusTotal API key | `abc123...` |
| `CORS_ALLOWED_ORIGIN` | Frontend URL | `https://myapp.railway.app` |
| `APP_BASE_URL` | Backend URL (keep-alive) | `https://myapi.onrender.com` |

### Frontend (Build-time)
| Variable | Description | Example |
|---|---|---|
| `VITE_API_URL` | Backend API base URL | `https://sftg-backend.onrender.com/api` |

---

## 🐳 Docker Deployment

### Build Images
```bash
# Backend
docker build -t yourusername/sftg-backend:latest \
  ./app/backendJava/SFTG-backend

# Frontend
docker build \
  --build-arg VITE_API_URL=https://your-backend.onrender.com/api \
  -t yourusername/sftg-frontend:latest \
  ./app/backendJava/SFTG-frontend
```

### Push to Docker Hub
```bash
docker push yourusername/sftg-backend:latest
docker push yourusername/sftg-frontend:latest
```

### Local Full Stack
```bash
docker-compose up --build
```

---

## ☁️ Cloud Deployment

### Current Setup
| Service | Platform | Notes |
|---|---|---|
| Backend | Render (free) | Cold start ~90s, keep-alive via UptimeRobot |
| Frontend | Railway (free) | Always warm, instant load |
| Database | Railway MySQL | 1GB free monthly |
| File Storage | Cloudinary | 25GB free tier |
| Email | Brevo | 300 emails/day free |
| Virus Scan | VirusTotal | 500 requests/day free |
| Monitoring | UptimeRobot | 5-minute pings |

### Deploy Backend (Render)
1. Create Web Service → Deploy existing Docker image
2. Image: `docker.io/yourusername/sftg-backend:latest`
3. Port: `8000`
4. Add all environment variables
5. Deploy

### Deploy Frontend (Railway)
1. Add Service → Docker Image
2. Image: `docker.io/yourusername/sftg-frontend:latest`
3. Networking → Generate Domain
4. Port: `80`

---

## 📁 Project Structure

```
SFTG/
├── app/
│   ├── backendJava/
│   │   ├── SFTG-backend/
│   │   │   ├── src/main/java/com/securetransfer/SFTG/
│   │   │   │   ├── config/          # CORS, Cloudinary, AppConfig
│   │   │   │   ├── controller/      # Auth, File, Share, Account, Stats
│   │   │   │   ├── dto/             # Request/Response objects
│   │   │   │   ├── exception/       # Custom exceptions + global handler
│   │   │   │   ├── model/           # JPA entities
│   │   │   │   ├── repository/      # JPA repositories
│   │   │   │   ├── security/        # JWT filter, SecurityConfig, rate limiting
│   │   │   │   └── service/         # Business logic layer
│   │   │   ├── Dockerfile
│   │   │   └── pom.xml
│   │   │
│   │   └── SFTG-frontend/
│   │       ├── src/
│   │       │   ├── api/             # Axios instance with interceptors
│   │       │   ├── components/      # Navbar, Layout, ProtectedRoute
│   │       │   └── pages/           # Login, Register, Dashboard, Upload, etc.
│   │       ├── public/
│   │       │   └── _redirects       # Netlify/Railway React Router support
│   │       ├── nginx.conf
│   │       └── Dockerfile
│   │
├── docker-compose.yml
└── README.md
```

---

## 🧠 Key Engineering Decisions

### 1. DB-Backed Token Blacklist
In-memory blacklists reset on server restart, making logged-out tokens valid again. Using MySQL with an index on the token column gives fast lookups with restart-proof persistence. A scheduled job cleans expired tokens hourly to keep the table small.

### 2. Non-Blocking Virus Scanner
ClamAV and VirusTotal are treated as non-blocking services. `ConnectException` and timeouts allow the upload to proceed with a warning log rather than failing the user request. Only a confirmed `MALICIOUS` result blocks the upload. This matches how production systems handle optional security services that may be temporarily unavailable.

### 3. Cascade Delete Order
Deleting a file requires deleting in FK-constrained order: `download_logs → shared_links → files`. The scheduler handles this explicitly in a `@Transactional` method rather than relying on JPA cascade, giving explicit control over the deletion sequence.

### 4. DTO Pattern Throughout
No JPA entity is ever returned directly from a controller. DTOs decouple the persistence layer from the API layer, prevent accidental data leaks, and allow the database schema to evolve without breaking API consumers.

### 5. MIME Detection Over Extension Checking
File extensions are trivially spoofed (`malware.exe` renamed to `document.pdf`). Apache Tika reads the actual file bytes to determine the real content type, making extension-based attacks ineffective.

---

## 🔮 Future Improvements

- [ ] Refresh token strategy (15-min access + 7-day refresh)
- [ ] Unit and integration tests (JUnit 5 + Mockito)
- [ ] API versioning (`/api/v1/`)
- [ ] File encryption at rest
- [ ] Redis for token blacklist (faster than MySQL)
- [ ] CI/CD pipeline (GitHub Actions)
- [ ] Admin dashboard for user management
- [ ] Configurable file expiry per upload

---

## 👨‍💻 Author

**Anup Kumar Sharma**  
Computer Science Engineering Graduate, 2025  
Backend Developer — Security-Focused Engineering

- GitHub: [@anupkumar9705](https://github.com/anupkumar9705)
- Docker Hub: [anupkumar9705](https://hub.docker.com/u/anupkumar9705)

---

## 📄 License

This project is developed for educational and portfolio demonstration purposes.

---

<div align="center">
  <strong>Built with production-grade security patterns for Fintech readiness</strong><br/>
  <sub>Spring Boot · React · Docker · JWT · ClamAV · Cloudinary · VirusTotal</sub>
</div>
