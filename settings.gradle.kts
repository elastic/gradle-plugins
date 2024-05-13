import org.gradle.internal.jvm.Jvm

plugins {
    id("com.gradle.enterprise").version("3.9")
    id("co.elastic.elastic-conventions").version("0.0.7")
}

include("libs")
include("libs:test-utils")
include("libs:utils")
include("libs:docker")
include("plugins")
include("plugins:vault")
include("plugins:sandbox")
include("plugins:docker:base-image")
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