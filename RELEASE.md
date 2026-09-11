# Releasing to plugins.gradle.org

The Buildkite pipeline publishes tagged releases after the amd64 and arm64 checks pass. The tag must exactly match the
version in `version-next`; a mismatched tag fails without publishing.

Before using this flow for the first time, merge the pipeline configuration and wait for Terrazzo to apply the
`catalog-info.yaml` change. Confirm that **Build Tags** is enabled in the pipeline's repository settings before pushing
the release tag.

1. Merge the release-ready commit to `main` and confirm that `version-next` contains the version to publish.
2. From an up-to-date `main`, create and push an annotated tag:

   ```bash
   VERSION="$(<version-next)"
   git tag -a "$VERSION" -m "Release $VERSION"
   git push origin "$VERSION"
   ```

3. Wait for the tagged build in the [Gradle Plugins CI pipeline](https://buildkite.com/elastic/gradle-plugins) to pass and
   confirm the version is visible on [plugins.gradle.org](https://plugins.gradle.org/).
4. Create a GitHub Release for that tag.
5. Submit a PR updating `version-released` to the published version and `version-next` to the next planned version.

## Publishing credentials

The pipeline reads the shared Gradle Plugin Portal key and secret from CI Vault only inside the publishing job. The
`elastic/gradle-plugins` repository is already granted access in Terrazzo; do not copy the credential to a developer
machine or print it in build output.

If the Vault read is denied, follow the internal
[Gradle Plugin Portal publishing guide](https://codex.elastic.dev/r/platform-engineering-productivity/releasing-software-at-elastic/releasing-on-prem-outside-of-the-unified-release/publishing-to-the-gradle-plugin-portal)
and open an Artifact Management request through [ela.st/engprod-request](https://ela.st/engprod-request). Include the
repository, Buildkite pipeline, owning team, plugin IDs or namespace, and planned release date.
