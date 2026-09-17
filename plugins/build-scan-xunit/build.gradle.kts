gradlePlugin {
    plugins {
        testSourceSets(java.sourceSets.integrationTest.get())
        create("co.elastic.build-scan.xunit") {
            id = "co.elastic.build-scan.xunit"
            implementationClass = "co.elastic.gradle.buildscan.xunit.XUnitBuildScanImporterPlugin"
            displayName = "Elastic Import Xunit to Build Scan"
            description = "Utilities to import xunit into build scans"
        }
    }
}

dependencies {
    implementation(project(":libs:utils"))

    integrationTestImplementation("commons-io:commons-io:2.22.0")
}

tasks.processIntegrationTestResources {
    // Re-use the test resources (only) in the integration tests
    from("src/test/resources")
}