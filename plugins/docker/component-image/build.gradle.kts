gradlePlugin {
    plugins {
        testSourceSets(java.sourceSets.integrationTest.get())
        create("co.elastic.docker-component") {
            id = "co.elastic.docker-component"
            implementationClass = "co.elastic.gradle.dockercomponent.DockerComponentPlugin"
            displayName = "Elastic Component Image"
            description = "Opinionated way to build container images using jib"
        }
    }
}

dependencies {
    implementation(project(":libs:docker"))
    implementation(project(":libs:utils"))

    implementation(project(":plugins:lifecycle"))

    implementation(project(":plugins:docker:base-image"))
    implementation(project(":plugins:cli:manifest-tool"))
    implementation(project(":plugins:cli:snyk"))

    val jacksonVersion = "2.17.2"
    implementation("com.fasterxml.jackson.core:jackson-databind:$jacksonVersion")
    implementation("com.fasterxml.jackson.dataformat:jackson-dataformat-yaml:$jacksonVersion")
    implementation("com.google.cloud.tools:jib-core:0.27.1")
    implementation("com.google.jimfs:jimfs:1.3.0")
    runtimeOnly("com.github.luben:zstd-jni:1.5.6-4")
    implementation("org.apache.commons:commons-compress:1.27.1")
    implementation("commons-io:commons-io:2.16.1")
    implementation("org.jetbrains:annotations:24.1.0")

    integrationTestImplementation("commons-io:commons-io:2.16.1")
    integrationTestImplementation("com.squareup.okhttp:okhttp:2.7.5")
    integrationTestImplementation("com.fasterxml.jackson.core:jackson-databind:$jacksonVersion")
    integrationTestImplementation(project(":libs:utils"))

    // This is really only needed for the test runtime, but if declared like that it's not found by buildkit
    implementation(project(":plugins:sandbox"))
    integrationTestImplementation(project(":plugins:sandbox"))
    implementation(project(":plugins:vault"))
    integrationTestImplementation(project(":plugins:docker:base-image"))
    integrationTestImplementation(project(":plugins:vault"))
    integrationTestImplementation(project(":plugins:lifecycle"))
}
