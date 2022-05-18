gradlePlugin {
    plugins {
        testSourceSets(java.sourceSets.integrationTest.get())
        create("co.elastic.cli.jfrog") {
            id = "co.elastic.cli.jfrog"
            implementationClass = "co.elastic.gradle.cli.jfrog.JFrogPlugin"
        }
    }
}

dependencies {
    implementation(project(":plugins:cli:base"))
    implementation(project(":libs:utils"))
    // This is really only needed for the test runtime, but if declared like that it's not found by buildkit
    implementation(project(":plugins:vault"))
}