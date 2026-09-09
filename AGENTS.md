# Agent guidance

## Repository purpose

See [README.md](README.md) for why this repository exists, who or what uses it, and its responsibility boundary.

## Secret scanning

**Never place credentials, tokens, private keys, cookies, or production secret values in tracked files, examples, tests, prompts, logs, or generated output.** Use the approved Vault-backed secret store and runtime injection mechanism instead.

Secret scanning controls are layered:

- **Pre-commit hook** — `elastic/gitleaks-hooks` at `v1.0.0` runs Gitleaks via `./bin/gitleaks` (Hermit-managed, v8.30.1). Install once with `pre-commit install`. The hook scans staged content before each commit.
- **GitHub secret scanning** — enabled, with push protection and non-provider patterns (private keys, connection strings, authorization headers). Push protection blocks a matching secret at `git push`.
- **Buildkite CI** — `elastic/hermit#v1.0.2` + `elastic/pre-commit#v1.0.3` plugins run the normal pre-commit hook set (including gitleaks) on every PR via the `gradle-plugins-ci` pipeline.

If Gitleaks detects a secret, treat the finding as exposed, stop immediately, and rotate or revoke the credential before pushing. Never bypass the hook with `--no-verify`, `SKIP=gitleaks`, an allowlist entry, or a GitHub push-protection bypass reason unless the repository owner explicitly authorizes that exact override after reviewing the finding. A request to complete, commit, push, or open a pull request does not constitute override authorization.
