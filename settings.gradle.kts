import java.io.File

plugins {
    id("com.gradle.develocity").version("3.18.1")
    id("co.elastic.elastic-conventions").version(File("version-released").readText().trim())
}

// Bootstrap this repository with the cache behavior implemented by the plugin being built below.
// Remove this block once version-released contains that implementation.
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
include("plugins")
include("plugins:vault")
include("plugins:sandbox")
include("plugins:elastic-conventions")
include("plugins:license-headers")
include("plugins:build-scan-xunit")
include("plugins:lifecycle")
include("plugins:cli")
include("plugins:cli:cli-lib")
include("plugins:cli:jfrog")
include("plugins:cli:shellcheck")
include("plugins:cli:snyk")
include("plugins:check-in-generated")
include("plugins:wrapper-provision-jdk")
