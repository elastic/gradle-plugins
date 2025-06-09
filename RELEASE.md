# Manual Releasing to plugins.gradle.org

1. `./gradlew "-Pgradle.publish.secret=$(vault kv get --field=secret kv/ci-shared/release-eng/team-release-secrets/gradle-plugins/gradle_plugin_portal)" "-Pgradle.publish.key=$(vault kv get --field=key kv/ci-shared/release-eng/team-release-secrets/gradle-plugins/gradle_plugin_portal)" publishPlugins`
1. Create a GitHub Release for the commit that's on `main` that has just been released
  1. Browse to [the releases page](https://github.com/elastic/gradle-plugins/releases)
  1. Click onto the latest release, which should be in Draft status
  1. Verify that all PRs are showing up as the correctly categories
  1 - If they are not, you will need to go into the PR itself, add the label, and then wait for Release Drafter to re-run
  1 - If it hasn't run, you can manually nudge it [via](https://github.com/elastic/gradle-plugins/actions/workflows/release-drafter.yaml)
  1 - Note that the next release version will be determined based on the labels in use
  1. Click on the pencil icon (to edit)
  1. Make any further tweaks to the release notes (as they are overridden whenever labels change on PRs)
  1. Hit publish release
2. Submit a PR bumping version-released and version-next after publishing to plugins.gradle.org
