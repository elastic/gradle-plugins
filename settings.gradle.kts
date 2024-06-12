import co.elastic.gradle.vault.VaultExtension
import java.io.File

plugins {
    id("com.gradle.enterprise").version("3.9")
    id("co.elastic.elastic-conventions").version(File("version-released").readText().trim())
    id("co.elastic.vault").version(File("version-released").readText().trim())
}

val vault:VaultExtension = extensions.findByType()!!
val creds:Map<String, String> = vault.readAndCacheSecret("secret/ci/elastic-gradle-plugins/cloud-build-cache-us-east1").get()

gradleEnterprise {
    buildCache {
        val isRunningInCI = System.getenv("BUILD_URL") != null || System.getenv("CI") == "true"
        remote<HttpBuildCache> {
            isEnabled = true
            url = uri("https://cloud-gradle-cache-us-east1.elastic.dev/cache/")
            isPush = isRunningInCI
            credentials {
                username = creds["username"]
                password = creds["password"]
            }
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
