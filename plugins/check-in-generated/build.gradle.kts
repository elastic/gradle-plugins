gradlePlugin {
    plugins {
        testSourceSets(java.sourceSets.integrationTest.get())
        create("co.elastic.check-in-generated") {
            id = "co.elastic.check-in-generated"
            implementationClass = "co.elastic.gradle.cig.CheckInGeneratedPlugin"
            displayName = "Elastic Check In Generated Sources"
            description = "Utilities for keeping generated code checked in in sync with the generation process"
        }
    }
}

dependencies {
    implementation("commons-io:commons-io:2.16.1")
}