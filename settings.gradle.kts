import org.gradle.internal.jvm.Jvm
import java.time.OffsetDateTime
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter.ISO_ZONED_DATE_TIME

plugins {
    id("com.gradle.enterprise").version("3.9")
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


gradleEnterprise {
    val isRunningInCI = System.getenv("BUILD_URL") != null

    // Don't upload a build scan for IDEA model updates, there will be a lot of these, especially with auto config turned on
    if (gradle.startParameter.taskNames != listOf("prepareKotlinBuildScriptModel")) {
        buildScan {

            // Not covered by the conventions plugin
            buildScanPublished {
                file(".build-scan.url").writeText("$buildScanUri")
            }

            publishAlways()
            isUploadInBackground = !isRunningInCI

            server = "https://gradle-enterprise.elastic.co"

            // Not covered by the conventions plugin
            capture.isTaskInputFiles = true

            obfuscation {
                ipAddresses { addresses -> addresses.map { address -> "0.0.0.0" } }
            }

            val jvm = Jvm.current()
            value("Java Home", jvm.javaHome.absolutePath)
            value("Java Version", jvm.javaVersion.toString())

            val buildNumber = System.getenv("BUILD_NUMBER")
            val buildUrl = System.getenv("BUILD_URL")
            val jobName = System.getenv("JOB_NAME")

            // Jenkins-specific build scan metadata
            if (buildUrl == null) {
                tag("LOCAL")
            } else {
                tag("CI")
                link("Jenkins Build", buildUrl)

                if (buildNumber != null) {
                    value("Job Number", buildNumber)
                }

                System.getenv().getOrDefault("NODE_LABELS", "").split(' ').forEach {
                    value("Jenkins Worker Label", it)
                }

                if (jobName != null) {
                    tag(jobName)
                    value("Job Name", jobName)
                }

                val changeTarget = System.getenv("CHANGE_TARGET")
                if (changeTarget != null) {
                    value("PR target", changeTarget)
                }
            }

            // Execute a CLI command and return the output
            val execute = { p: String ->
                ProcessBuilder(p.split(" ")).start().apply { waitFor() }.inputStream.bufferedReader()
                    .use { it.readText().trim() }
            }
            gradle.buildFinished {
                // Git commit id
                val commitId = execute("git rev-parse --verify HEAD")
                if (!commitId.isNullOrEmpty()) {
                    value("Git Commit", commitId)
                    link("Source", "https://github.com/elastic/cloud/tree/$commitId")
                }

                // Git parent commit id
                val parrentCommitId = execute("git rev-parse --verify HEAD^1")
                if (!commitId.isNullOrEmpty()) {
                    value("Git Parent", parrentCommitId)
                }

                // Git branch name
                val branchName = execute("git rev-parse --abbrev-ref HEAD")
                if (!branchName.isNullOrEmpty()) {
                    buildScan.value("Git Branch", branchName)
                }

                // Git dirty local state
                val status = execute("git status --porcelain")
                if (!status.isNullOrEmpty()) {
                    buildScan.tag("dirty")
                    buildScan.value("Git Status", status)
                }
            }
        }
    }
}