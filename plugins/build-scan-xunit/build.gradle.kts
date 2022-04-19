gradlePlugin {
    plugins {
        testSourceSets(java.sourceSets.integrationTest.get())
        create("co.elastic.build-scan.xunit") {
            id = "co.elastic.build-scan.xunit"
            implementationClass = "co.elastic.gradle.buildscan.xunit.XUnitBuildScanImporterPlugin"
        }
    }
}

dependencies {
    implementation(project(":libs:utils"))

    // We only use this for integration tests, but buildkit needs it here
    implementation(project(":plugins:sandbox"))
    integrationTestImplementation(project(":plugins:sandbox"))
    integrationTestImplementation("commons-io:commons-io:2.11.0")
}

tasks.processIntegrationTestResources {
    // Re-use the test resources (only) in the integration tests
    from("src/test/resources")
}