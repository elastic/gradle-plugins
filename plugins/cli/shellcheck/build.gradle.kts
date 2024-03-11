gradlePlugin {
    plugins {
        testSourceSets(java.sourceSets.integrationTest.get())
        create("co.elastic.cli.shellcheck") {
            id = "co.elastic.cli.shellcheck"
            implementationClass = "co.elastic.gradle.cli.shellcheck.ShellcheckPlugin"
            displayName = "Elastic CLI plugin for shellcheck"
            description = "Provision and make it easy to use the shellcheck cli"
        }
    }
}

dependencies {
    implementation(project(":plugins:cli:base"))
    implementation(project(":plugins:lifecycle"))
    implementation(project(":libs:utils"))
    runtimeOnly("org.tukaani:xz:1.8")
    // This is really only needed for the test runtime, but if declared like that it's not found by buildkit
    implementation(project(":plugins:vault"))
}

