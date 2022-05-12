gradlePlugin {
    plugins {
        testSourceSets(java.sourceSets.integrationTest.get())
        create("co.elastic.wrapper-provision-jdk") {
            id = "co.elastic.wrapper-provision-jdk"
            implementationClass = "co.elastic.gradle.wrapper.WrapperPlugin"
        }
    }
}

dependencies {
    implementation(project(":libs:utils"))
    integrationTestImplementation(project(":libs:utils"))
    integrationTestImplementation("commons-io:commons-io:2.11.0")
}