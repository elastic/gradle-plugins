import java.io.File

plugins {
    id("com.gradle.develocity").version("4.2.2")
    id("co.elastic.elastic-conventions").version(File("version-released").readText().trim())
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
