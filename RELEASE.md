# Manual Releasing to plugins.gradle.org

1. `./gradlew "-Pgradle.publish.secret=$(vault read --field=secret secret/ci/elastic-gradle-plugins/gradle-plugin-portal)" "-Pgradle.publish.key=$(vault read --field=key secret/ci/elastic-gradle-plugins/gradle-plugin-portal) publishPlugins`
2. Submit a PR bumping version-released and version-next after publishing to plugins.gradle.org