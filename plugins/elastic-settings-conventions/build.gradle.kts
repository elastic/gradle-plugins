gradlePlugin {
    plugins {
        testSourceSets(java.sourceSets.integrationTest.get())
        create("co.elastic.elastic-settings-conventions") {
            id = "co.elastic.elastic-settings-conventions"
            implementationClass = "co.elastic.gradle.elastic_settings_conventions.ElasticSettingsConventionsPlugin"
            displayName = "Elastic Settings Conventions Plugin"
            description = "Implement internal elastic settings conventions"
        }
    }
}


repositories {
    gradlePluginPortal()
}

dependencies {
    implementation("com.gradle.enterprise:com.gradle.enterprise.gradle.plugin:3.17.6")
    implementation("com.gradle:common-custom-user-data-gradle-plugin:1.7.2")
    implementation(project(":libs:utils"))
}
