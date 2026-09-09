# Agent guidance

## Repository purpose

See [README.md](README.md) for why this repository exists, who or what uses it, and its responsibility boundary.

## Secret scanning

**Never place credentials, tokens, private keys, cookies, or production secret values in tracked files, examples, tests, prompts, logs, or generated output.** Use the approved Vault-backed secret store and runtime injection mechanism instead.

Secret scanning controls are layered:

- **Pre-commit hook** — `elastic/gitleaks-hooks` at `v1.0.0` runs Gitleaks via `./bin/gitleaks` (Hermit-managed, v8.30.1). Install once with `pre-commit install`. The hook scans staged content before each commit.
- **GitHub secret scanning** — enabled, with push protection and non-provider patterns (private keys, connection strings, authorization headers). Push protection blocks a matching secret at `git push`.
- **Buildkite CI** — `elastic/hermit#v1.0.2` + `elastic/pre-commit#v1.0.3` plugins run the normal pre-commit hook set (including gitleaks) on every PR via the `gradle-plugins-ci` pipeline.

### Setting up the hook

Both humans and coding agents are expected to use this tooling; it is the first
layer that stops a secret before it becomes a commit.

`pre-commit` and Gitleaks are both pinned via Hermit, so no global install is
needed. From a fresh clone:

```bash
./bin/pre-commit install    # once per clone, installs the git hook
```

Thereafter the hook runs automatically on `git commit`. To run it on demand:

```bash
./bin/pre-commit run gitleaks              # scan staged content
./bin/pre-commit run gitleaks --all-files  # scan the whole tree
```

`./bin/pre-commit` and `./bin/gitleaks` are Hermit launchers and work without
activating Hermit. If you activate the environment (`. bin/activate-hermit`),
plain `pre-commit` and `gitleaks` resolve to the same pinned versions. Python
3.9 or newer must be available: `pre-commit` uses it to build the hook's
isolated environment, though it does not provision Gitleaks itself.

If Gitleaks detects a secret, treat the finding as exposed, stop immediately, and rotate or revoke the credential before pushing. Never bypass the hook with `--no-verify`, `SKIP=gitleaks`, an allowlist entry, or a GitHub push-protection bypass reason unless the repository owner explicitly authorizes that exact override after reviewing the finding. A request to complete, commit, push, or open a pull request does not constitute override authorization.
