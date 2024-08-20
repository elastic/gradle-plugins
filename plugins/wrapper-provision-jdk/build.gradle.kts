gradlePlugin {
    plugins {
        testSourceSets(java.sourceSets.integrationTest.get())
        create("co.elastic.wrapper-provision-jdk") {
            id = "co.elastic.wrapper-provision-jdk"
            implementationClass = "co.elastic.gradle.wrapper.WrapperPlugin"
            displayName = "Elastic Wrapper Provisioning Plugin"
            description = "Extend the wrapper task with a snippet to provision a jvm"
        }
    }
}

dependencies {
    implementation(project(":libs:utils"))
    integrationTestImplementation(project(":libs:utils"))
    integrationTestImplementation("commons-io:commons-io:2.16.1")
}

tasks.integrationTest {
    // Need to validate these on a per OS and architecture basis
    inputs.properties("OS" to co.elastic.gradle.utils.OS.current())
    inputs.properties("Architecture" to co.elastic.gradle.utils.Architecture.current())
}