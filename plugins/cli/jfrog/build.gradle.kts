gradlePlugin {
    plugins {
        testSourceSets(java.sourceSets.integrationTest.get())
        create("co.elastic.cli.jfrog") {
            id = "co.elastic.cli.jfrog"
            implementationClass = "co.elastic.gradle.cli.jfrog.JFrogPlugin"
            displayName = "Elastic CLI Plugin to use jfrog"
            description = "Plugin that provisions and maks it easy to use the jfrog cli"
        }
    }
}

dependencies {
    implementation(project(":plugins:cli:cli-lib"))
    implementation(project(":libs:utils"))
    // This is really only needed for the test runtime, but if declared like that it's not found by buildkit
    implementation(project(":plugins:vault"))
}