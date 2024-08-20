gradlePlugin {
    plugins {
        testSourceSets(java.sourceSets.integrationTest.get())
        create("co.elastic.gradle.docker-base") {
            id = "co.elastic.docker-base"
            implementationClass = "co.elastic.gradle.dockerbase.DockerBaseImageBuildPlugin"
            displayName = "Elastic Docker Base Image"
            description = "Oppinionated way to build docker base images"
        }
    }
}

dependencies {
    implementation("org.apache.commons:commons-compress:1.27.0")
    implementation("commons-io:commons-io:2.16.1")
    implementation(project(":libs:docker"))
    implementation(project(":libs:utils"))

    implementation(project(":plugins:cli:jfrog"))
    implementation(project(":plugins:docker:docker-lib"))
    implementation(project(":plugins:lifecycle"))

    val jacksonVersion = "2.17.2"
    implementation("com.fasterxml.jackson.core:jackson-databind:$jacksonVersion")
    implementation("com.fasterxml.jackson.dataformat:jackson-dataformat-yaml:$jacksonVersion")
    implementation("org.jetbrains:annotations:24.1.0")
    implementation("org.apache.commons:commons-csv:1.11.0")

    runtimeOnly("com.github.luben:zstd-jni:1.5.6-4")

    // Fixme: remove dependency from base image
    implementation("com.google.cloud.tools:jib-core:0.27.1")

    // This is really only needed for the test runtime, but if declared like that it's not found by buildkit
    implementation(project(":plugins:vault"))
    integrationTestImplementation(project(":plugins:vault"))
    implementation(project(":plugins:sandbox"))
    integrationTestImplementation(project(":plugins:sandbox"))

    integrationTestImplementation("commons-io:commons-io:2.16.1")
    integrationTestImplementation("com.squareup.okhttp:okhttp:2.7.5")
    integrationTestImplementation("com.fasterxml.jackson.core:jackson-databind:$jacksonVersion")
    integrationTestImplementation(project(":libs:utils"))
}

tasks.integrationTest {
    // Need to validate these on a per OS and architecture basis
    inputs.properties("OS" to co.elastic.gradle.utils.OS.current())
    inputs.properties("Architecture" to co.elastic.gradle.utils.Architecture.current())
}