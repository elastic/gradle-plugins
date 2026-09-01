import java.io.File

plugins {
    id("com.gradle.develocity").version("3.18.1")
    id("co.elastic.elastic-conventions").version(File("version-released").readText().trim())
}

develocity {
    buildCache {
        val isRunningInCI = System.getenv("BUILD_URL") != null || System.getenv("BUILDKITE_BUILD_URL") != null
        remote(develocity.buildCache) {
            isEnabled = true
            isPush = isRunningInCI
        }
    }
}


include("libs")
include("libs:test-utils")
include("libs:utils")
include("libs:docker")
include("plugins")
include("plugins:vault")
include("plugins:sandbox")
include("plugins:docker:component-image")
include("plugins:docker:docker-lib")
include("plugins:elastic-conventions")
include("plugins:license-headers")
include("plugins:build-scan-xunit")
include("plugins:lifecycle")
include("plugins:cli")
include("plugins:cli:cli-lib")
include("plugins:cli:jfrog")
include("plugins:cli:manifest-tool")
include("plugins:cli:shellcheck")
include("plugins:cli:snyk")
include("plugins:check-in-generated")
include("plugins:wrapper-provision-jdk")
