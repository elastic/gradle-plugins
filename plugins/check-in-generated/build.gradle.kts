gradlePlugin {
    plugins {
        testSourceSets(java.sourceSets.integrationTest.get())
        create("co.elastic.check-in-generated") {
            id = "co.elastic.check-in-generated"
            implementationClass = "co.elastic.gradle.cig.CheckInGeneratedPlugin"
        }
    }
}

dependencies {
    implementation("commons-io:commons-io:2.11.0")
}