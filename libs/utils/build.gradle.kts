plugins {
    `java-library`
}

dependencies {
    implementation(gradleApi())
    implementation("org.apache.commons:commons-compress:1.28.0")
    implementation("commons-io:commons-io:2.22.0")
    testImplementation("org.junit.jupiter:junit-jupiter:5.14.4")
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