# ADR 002: Non-Blocking Virus Scanning

**Date:** 2025-01-15
**Status:** Accepted
**Deciders:** Anup Kumar Sharma

## Context

Virus scanning (VirusTotal v3 API) is a critical security layer for file uploads. However, the scanning service can be:

- **Unavailable** (VirusTotal API down or unreachable)
- **Slow** (large files, network latency, analysis queue)
- **Rate-limited** (VirusTotal free tier: ~4 req/min, 500/day)
- **Misconfigured** (no API key present)

Blocking uploads on scanner failure creates availability issues:
- Legitimate users cannot upload during scanner downtime
- DoS vector: attacker overwhelms scanner, blocks all uploads
- Free tier limits make cloud scanning unreliable

We also rejected running a local ClamAV daemon: its signature database needs
roughly 1 GB of RAM, which does not fit the 1–2 GB free cloud instances this
project targets.

## Decision

**Treat virus scanning as non-blocking: allow upload on scanner failure, only block on confirmed MALICIOUS result.**

### Implementation Details

```java
// FileService.storeFile()
try {
    virusTotalService.scanFile(targetLocation);
} catch (Exception ex) {
    Files.deleteIfExists(targetLocation); // cleanup infected file
    throw ex; // re-throw to block upload
}

// VirusTotalService.scanFile() — hash first, upload only when unknown:
public void scanFile(Path filePath) {
    if (apiKey is blank) {
        log.warn("API key not configured — skipping scan");
        return;                       // scans disabled, uploads continue
    }
    try {
        JsonNode report = get("/files/" + sha256(filePath));
        if (report != null) {
            evaluate(report.last_analysis_stats);   // blocks if malicious > 0
            return;                                 // no upload, no quota spent
        }
        if (!uploadUnknown) return;
        String analysisId = upload(filePath);       // >32 MB → /files/upload_url
        pollAnalysis(analysisId);                   // configurable attempts
    } catch (FileStorageException e) {
        throw e;                       // confirmed malware → block upload
    } catch (Exception e) {
        log.error("Scan unavailable — allowing upload: {}", e.getMessage());
    }
}
```

### VirusTotal Integration (Cloud)

- **Hash lookup first**: SHA-256 the file and query `/files/{hash}` — most
  files are already known, so nothing is uploaded and no analysis wait occurs.
- **Upload only unknown files**: respects the free-tier quota.
- **Bounded polling**: `virustotal.poll-attempts` × `poll-interval-ms`
  (default 2 × 20 s); an unfinished analysis allows the upload with a warning.
- **Disabled by default without a key**: local development works offline.

### Error Classification

| Scanner Result | Action |
|----------------|--------|
| `CLEAN` | Allow upload |
| `MALICIOUS` | **Block upload**, delete file, log security event |
| `ConnectException` / `Timeout` | **Allow upload**, log warning, alert ops |
| `RateLimit` (VirusTotal 429) | **Allow upload**, log warning |
| Missing API key | **Allow upload**, log warning (scans disabled) |
| Any other error | **Allow upload**, log error |

## Consequences

### Positive
- ✅ Upload availability not dependent on scanner health
- ✅ No DoS via scanner overload
- ✅ Works within free tier limits (graceful degradation)
- ✅ Security event logged for every scan outcome
- ✅ Confirmed malware still blocked

### Negative
- ⚠️ Brief window where malicious file could be uploaded if scanner down
- ⚠️ Requires monitoring/alerting on scanner failures
- ⚠️ Cloud VirusTotal results can lag behind upload (hash-unknown files)

### Neutral
- 📝 Mitigation: Alert on scanner down → manual review of recent uploads
- 📝 Future: Quarantine zone for "scanner unavailable" uploads
- 📝 Future: Redis cache of known-good/bad hashes to reduce scans

## Alternatives Considered

| Alternative | Rejected Because |
|-------------|------------------|
| Block on any scanner failure | Availability SLA violated; scanner becomes DoS target |
| Queue files for later scan | Complexity; user experience suffers (wait for scan) |
| Require VirusTotal paid tier | Cost; not justified for portfolio project |
| Skip scanning entirely | Unacceptable security risk for file-sharing app |

## Monitoring Requirements

- **Alert**: VirusTotal errors (HTTP 5xx / connection failures) > 5 min
- **Alert**: VirusTotal rate limit (HTTP 429) hit
- **Dashboard**: Scan results (clean/malicious/error) over time
- **Runbook**: Manual scan of recent uploads when scanner recovers

## Related ADRs

- ADR 001: DB-Backed JWT Blacklist (similar: graceful degradation pattern)
- ADR 003: MIME Detection via Tika (layered security)