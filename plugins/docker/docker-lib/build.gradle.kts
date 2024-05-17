gradlePlugin {
    plugins {
        create("co.elastic.docker-lib") {
            id = "co.elastic.docker-lib"
            displayName = "Elastic Docker Base Image Library"
            description = "Library of shared tools between docker plugins"
            implementationClass = "co.elastic.gradle.docker.base.DontApplyPlugin"
        }
    }
}

dependencies {
    implementation(project(":libs:docker"))
    implementation(project(":libs:utils"))
    runtimeOnly("com.github.luben:zstd-jni:1.5.0-4")
    implementation("org.apache.commons:commons-compress:1.26.1")
    implementation("commons-io:commons-io:2.11.0")
}
