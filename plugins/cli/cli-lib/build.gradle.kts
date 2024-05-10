dependencies {
    implementation(project(":libs:utils"))
    implementation(project(":plugins:lifecycle"))
    implementation("org.apache.commons:commons-compress:1.26.1")
    implementation("commons-io:commons-io:2.11.0")
}


gradlePlugin {
    plugins {
        create("co.elastic.cli-lib") {
            id = "co.elastic.cli-lib"
            displayName = "Elastic CLI Plugin Library"
            description = "Library of shared tools between cli plugins"
            implementationClass = "co.elastic.gradle.cli.base.DontApplyPlugin"
        }
    }
}