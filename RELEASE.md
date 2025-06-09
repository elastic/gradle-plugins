# Manual Releasing to plugins.gradle.org

1. `./gradlew "-Pgradle.publish.secret=$(vault kv get --field=secret kv/ci-shared/release-eng/team-release-secrets/gradle-plugins/gradle_plugin_portal)" "-Pgradle.publish.key=$(vault kv get --field=key kv/ci-shared/release-eng/team-release-secrets/gradle-plugins/gradle_plugin_portal)" publishPlugins`
1. Create a GitHub Release for the commit that's on `main` that has just been released
2. Submit a PR bumping version-released and version-next after publishing to plugins.gradle.org
