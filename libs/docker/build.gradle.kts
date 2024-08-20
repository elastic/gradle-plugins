plugins {
    `java-library`
}

dependencies {
    implementation(gradleApi())
    implementation(project(":libs:utils"))
    implementation("commons-io:commons-io:2.16.1")
    testImplementation("org.junit.jupiter:junit-jupiter:5.11.0")
    testImplementation("org.mockito:mockito-all:1.10.19")
}

tasks.test {
    useJUnitPlatform()
}

gradlePlugin {
    plugins {
        create("co.elastic.docker") {
            id = "co.elastic.docker"
            displayName = "Elastic Docker Library"
            description = "Library of shared tools between docker plugins"
            implementationClass = "co.elastic.gradle.utils.docker.DontApplyPlugin"
        }
    }
}