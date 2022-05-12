gradlePlugin {
    plugins {
        testSourceSets(java.sourceSets.integrationTest.get())
        create("co.elastic.sandbox") {
            id = "co.elastic.sandbox"
            implementationClass = "co.elastic.gradle.sandbox.SandboxPlugin"
        }
    }
}

dependencies {
    api(project(":libs:docker"))
    implementation(project(":libs:utils"))
    implementation(project(":plugins:lifecycle"))
}