gradlePlugin {
    plugins {
        testSourceSets(java.sourceSets.integrationTest.get())
        create("co.elastic.gradle.docker-base") {
            id = "co.elastic.docker-base"
            implementationClass = "co.elastic.gradle.dockerbase.DockerBaseImageBuildPlugin"
        }
    }
}

dependencies {
    implementation("org.apache.commons:commons-compress:1.21")
    implementation("commons-io:commons-io:2.11.0")
    implementation(project(":libs:docker"))
    implementation(project(":libs:utils"))

    implementation(project(":plugins:lifecycle"))

    val jacksonVersion = "2.13.2"
    implementation("com.fasterxml.jackson.core:jackson-databind:$jacksonVersion")
    implementation("com.fasterxml.jackson.dataformat:jackson-dataformat-yaml:$jacksonVersion")
    implementation("org.jetbrains:annotations:23.0.0")
    implementation("org.apache.commons:commons-csv:1.9.0")

    runtimeOnly("com.github.luben:zstd-jni:1.5.0-4")

    // Fixme: remove dependency from base image
    implementation("com.google.cloud.tools:jib-core:0.21.0")

    // This is really only needed for the test runtime, but if declared like that it's not found by buildkit
    implementation(project(":plugins:vault"))
    integrationTestImplementation(project(":plugins:vault"))
    implementation(project(":plugins:sandbox"))
    integrationTestImplementation(project(":plugins:sandbox"))

    integrationTestImplementation("commons-io:commons-io:2.11.0")
    integrationTestImplementation("com.squareup.okhttp:okhttp:2.7.5")
    integrationTestImplementation("com.fasterxml.jackson.core:jackson-databind:$jacksonVersion")
    integrationTestImplementation(project(":libs:utils"))
}