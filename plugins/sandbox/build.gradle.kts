gradlePlugin {
    plugins {
        testSourceSets(java.sourceSets.integrationTest.get())
        create("co.elastic.sandbox") {
            id = "co.elastic.sandbox"
            implementationClass = "co.elastic.gradle.sandbox.SandboxPlugin"
            displayName = "Elastic Sandbox"
            description = "Implements sandbox tasks that guarantee no reliance on the local system"
        }
    }
}

dependencies {
    api(project(":libs:docker"))
    implementation(project(":libs:utils"))
    implementation(project(":plugins:lifecycle"))
}

tasks.integrationTest {
    // Need to validate these on a per OS and architecture basis
    inputs.properties("OS" to co.elastic.gradle.utils.OS.current())
    inputs.properties("Architecture" to co.elastic.gradle.utils.Architecture.current())
}