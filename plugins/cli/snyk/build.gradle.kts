gradlePlugin {
    plugins {
        testSourceSets(java.sourceSets.integrationTest.get())
        create("co.elastic.cli.snyk") {
            id = "co.elastic.cli.snyk"
            implementationClass = "co.elastic.gradle.snyk.SnykPlugin"
            displayName = "Elastic CLI plugin for snyk"
            description = "Provision and make the snyk cli easy to use"
        }
    }
}

dependencies {
    implementation(project(":plugins:cli:cli-lib"))
    implementation(project(":libs:utils"))
    // This is really only needed for the test runtime, but if declared like that it's not found by buildkit
    implementation(project(":plugins:vault"))
}