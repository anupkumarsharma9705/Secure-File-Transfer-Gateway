# ADR 003: MIME Detection via Apache Tika (Content-Based)

**Date:** 2025-01-15
**Status:** Accepted
**Deciders:** Anup Kumar Sharma

## Context

File type validation is critical for security. Common approaches:

1. **Extension-based** — Check `.pdf`, `.jpg`, etc. — Trivially spoofed (`malware.exe` → `document.pdf`)
2. **Magic bytes / MIME sniffing** — Read file header bytes — Standard but limited
3. **Apache Tika** — Content-based detection using comprehensive format parsers — Most accurate

### The Extension Spoofing Problem

```
File extensions are trivially spoofed:
  malware.exe renamed to document.pdf
  shell.sh renamed to image.png
  batch.bat renamed to data.txt

Extension-based validation:
  if (filename.endsWith(".exe")) reject;  // BYPASSED by rename
```

## Decision

**Use Apache Tika for content-based MIME type detection on every upload. Reject executable MIME types regardless of extension.**

### Implementation Details

```java
// FileService.storeFile() lines 130-143
private Tika tika;

@PostConstruct
public void init() {
    this.tika = new Tika();
}

// During upload:
String mimeType;
try (InputStream stream = Files.newInputStream(targetLocation)) {
    mimeType = tika.detect(stream, originalFilename);
} catch (IOException e) {
    mimeType = "application/octet-stream"; // Fallback
}

// Block executable MIME types (lines 138-143)
if (mimeType.equals("application/x-msdownload")    // .exe, .dll
        || mimeType.equals("application/x-sh")     // .sh
        || mimeType.equals("application/x-bat")) { // .bat
    Files.deleteIfExists(targetLocation);
    throw new FileStorageException("Executable files are not allowed");
}
```

### Blocked MIME Types

| MIME Type | Extensions | Reason |
|-----------|------------|--------|
| `application/x-msdownload` | .exe, .dll, .msi | Windows executables |
| `application/x-sh` | .sh | Shell scripts |
| `application/x-bat` | .bat, .cmd | Windows batch |
| `application/x-msdos-program` | .com, .pif | DOS executables |
| `application/java-archive` | .jar | Java executables (optional) |

### Defense in Depth (Layered)

1. **Extension check** — Fast reject for obvious cases (user feedback)
2. **Tika MIME detection** — Content-based, catches spoofed extensions
3. **Executable permission removal** — `chmod 644` on POSIX (line 107-113)
4. **Path traversal protection** — Normalize path, verify within upload dir (line 81-84, 189-191)
5. **Virus scanning** — VirusTotal (ADR 002)

## Consequences

### Positive
- ✅ Detects actual file content, not just name
- ✅ Catches polyglot files (valid image + embedded script)
- ✅ Comprehensive format support (1000+ types)
- ✅ Industry standard, actively maintained
- ✅ Blocks executables even with spoofed extensions

### Negative
- ⚠️ Additional dependency (tika-core ~2MB)
- ⚠️ Slight CPU overhead reading file headers
- ⚠️ False positives possible (rare, but Tika can misidentify)
- ⚠️ Must keep Tika updated for new formats

### Neutral
- 📝 Fallback to `application/octet-stream` on detection failure
- 📝 Could add custom detectors for proprietary formats
- 📝 Consider `tika-parsers-standard-package` for smaller footprint

## Alternatives Considered

| Alternative | Rejected Because |
|-------------|------------------|
| Extension only | Trivially bypassed; security theater |
| `Files.probeContentType()` | Limited to JDK built-in types; misses many formats |
| Custom magic bytes | Maintenance burden; incomplete coverage |
| `mime-util` / `jMimeMagic` | Less maintained; smaller format library |

## Security Testing

Test cases that must pass:
- [ ] `malware.exe` renamed to `document.pdf` → **REJECTED** (Tika detects `application/x-msdownload`)
- [ ] Valid PDF → **ACCEPTED** (`application/pdf`)
- [ ] Valid PNG → **ACCEPTED** (`image/png`)
- [ ] Polyglot: PNG + appended shell script → **REJECTED** (Tika detects script)
- [ ] Empty file → **ACCEPTED** (falls back to octet-stream)
- [ ] Corrupted file → **ACCEPTED** (falls back, virus scan catches)

## Related ADRs

- ADR 002: Non-Blocking Virus Scanning (next layer after MIME)
- ADR 004: Cascade Delete Order (cleanup on rejection)
- ADR 005: DTO Pattern (file metadata never exposes internal detection)

## Future Enhancements

- [ ] Add custom Tika detector for organization-specific formats
- [ ] Cache detection results by hash (avoid re-scanning identical files)
- [ ] Integrate with VirusTotal's own file-type labelling for cross-validation
- [ ] Consider `tika-parsers-standard-package` for smaller container image