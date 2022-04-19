gradlePlugin {
    plugins {
        testSourceSets(java.sourceSets.integrationTest.get())
        create("co.elastic.gradle.docker-component") {
            id = "co.elastic.gradle.docker-component"
            implementationClass = "co.elastic.gradle.dockercomponent.DockerComponentPlugin"
        }
    }
}

dependencies {
    implementation(project(":libs:docker"))
    implementation(project(":libs:utils"))

    implementation(project(":plugins:docker:base"))
    implementation(project(":plugins:cli:manifest-tool"))
    implementation(project(":plugins:cli:snyk"))

    implementation("com.google.cloud.tools:jib-core:0.20.0")
    implementation("com.google.jimfs:jimfs:1.2")
    runtimeOnly("com.github.luben:zstd-jni:1.5.0-4")
    implementation("org.apache.commons:commons-compress:1.21")
    implementation("commons-io:commons-io:2.11.0")
    implementation("org.jetbrains:annotations:23.0.0")
}