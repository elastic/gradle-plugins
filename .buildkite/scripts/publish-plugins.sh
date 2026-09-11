#!/usr/bin/env bash

set -euo pipefail

readonly REPOSITORY_ROOT="$(cd -- "$(dirname -- "${BASH_SOURCE[0]}")/../.." && pwd)"
readonly VAULT_PATH="kv/ci-shared/release-eng/team-release-secrets/gradle-plugins/gradle_plugin_portal"

cd "$REPOSITORY_ROOT"

: "${BUILDKITE_TAG:?This publishing script must only run for a tagged Buildkite build}"

release_version="$(<version-next)"
if [[ -z "$release_version" ]]; then
  echo "version-next is empty" >&2
  exit 1
fi

if [[ "$BUILDKITE_TAG" != "$release_version" ]]; then
  echo "Refusing to publish: tag '$BUILDKITE_TAG' does not match version-next '$release_version'" >&2
  exit 1
fi

GRADLE_PUBLISH_KEY="$(vault kv get --field=key "$VAULT_PATH")"
GRADLE_PUBLISH_SECRET="$(vault kv get --field=secret "$VAULT_PATH")"
export GRADLE_PUBLISH_KEY GRADLE_PUBLISH_SECRET

: "${GRADLE_PUBLISH_KEY:?Vault returned an empty Gradle Plugin Portal key}"
: "${GRADLE_PUBLISH_SECRET:?Vault returned an empty Gradle Plugin Portal secret}"

./gradlew publishPlugins
