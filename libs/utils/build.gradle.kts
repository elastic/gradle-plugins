plugins {
    `java-library`
}

dependencies {
    implementation(gradleApi())
    implementation("org.apache.commons:commons-compress:1.27.0")
    implementation("commons-io:commons-io:2.16.1")
    testImplementation("org.junit.jupiter:junit-jupiter:5.10.3")
}

tasks.test {
    useJUnitPlatform()
}

gradlePlugin {
    plugins {
        create("co.elastic.utils") {
            id = "co.elastic.utils"
            displayName = "Elastic Utilities Library"
            description = "Library of shared tools between elatic plugins"
            implementationClass = "co.elastic.gradle.utils.DontApplyPlugin"
        }
    }
}