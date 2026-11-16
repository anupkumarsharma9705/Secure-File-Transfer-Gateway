# Documentation Checklist

| File | Exists? | Customized? |
|------|---------|-------------|
| `README.md` | ☐ | ☐ |
| `ARCHITECTURE.md` | ☐ | ☐ |
| `docs/CONFIGURATION.md` | ☐ | ☐ |
| `docs/OPERATIONS.md` | ☐ | ☐ |
| `docs/ADR/0001-*.md` | ☐ | ☐ |
| Live URL in README | ☐ | — |
| Load results linked | ☐ | — |
| Gaps section honest | ☐ | — |

---

## Verification

```bash
# Check all exist
ls -la README.md ARCHITECTURE.md docs/CONFIGURATION.md docs/OPERATIONS.md docs/ADR/

# Check live URL works
curl -fsS https://project.yourname.dev/health
```