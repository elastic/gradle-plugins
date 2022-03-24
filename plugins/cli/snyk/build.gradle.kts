gradlePlugin {
    plugins {
        testSourceSets(java.sourceSets.integrationTest.get())
        create("co.elastic.cli.snyk") {
            id = "co.elastic.cli.snyk"
            implementationClass = "co.elastic.gradle.snyk.SnykPlugin"
        }
    }
}

dependencies {
    implementation(project(":plugins:cli:base"))
    implementation(project(":libs:utils"))
    // This is really only needed for the test runtime, but if declared like that it's not found by buildkit
    implementation(project(":plugins:vault"))
}