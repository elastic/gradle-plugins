gradlePlugin {
    plugins {
        testSourceSets(java.sourceSets.integrationTest.get())
        create("co.elastic.elastic-conventions") {
            id = "co.elastic.elastic-conventions"
            implementationClass = "co.elastic.gradle.elastic_conventions.ElasticConventionsPlugin"
        }
    }
}


repositories {
    gradlePluginPortal()
}

dependencies {
    implementation("com.gradle.enterprise:com.gradle.enterprise.gradle.plugin:3.10.1")
    implementation("com.gradle:common-custom-user-data-gradle-plugin:1.7.2")
    implementation(project(":libs:utils"))
    implementation(project(":plugins:lifecycle"))
    implementation(project(":plugins:vault"))
    integrationTestImplementation(project(":plugins:vault"))
}