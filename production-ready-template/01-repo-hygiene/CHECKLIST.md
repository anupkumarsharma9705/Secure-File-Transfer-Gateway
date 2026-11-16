# Repo Hygiene Checklist

**Tick every box before first push.**

| Item | Done? |
|------|-------|
| `LICENSE` present and intentional | ☐ |
| `.gitignore` excludes build, secrets, IDE, OS | ☐ |
| `.editorconfig` committed | ☐ |
| `.github/dependabot.yml` configured | ☐ |
| `README.md` from template, customized | ☐ |
| `ARCHITECTURE.md` from template, customized | ☐ |
| `SECURITY.md` from template, customized | ☐ |
| `docs/ADR/` directory exists | ☐ |
| Conventional commits configured (husky/commitlint) | ☐ |
| Branch protection on `main` (PR required, CI required) | ☐ |
| No secrets in history (run `git-secrets` or `trufflehog`) | ☐ |

---

## Verification Commands

```bash
# Check license
cat LICENSE | head -5

# Check gitignore works
git status --ignored | grep -E "target|.env|.idea"

# Check editorconfig
cat .editorconfig

# Check dependabot
cat .github/dependabot.yml

# Check no secrets
trufflehog git file://. --only-verified
```