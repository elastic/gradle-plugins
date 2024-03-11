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