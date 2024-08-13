gradlePlugin {
    plugins {
        testSourceSets(java.sourceSets.integrationTest.get())
        create("co.elastic.elastic-conventions") {
            id = "co.elastic.elastic-conventions"
            implementationClass = "co.elastic.gradle.elastic_conventions.ElasticConventionsPlugin"
            displayName = "Elastic Conventions Plugin"
            description = "Implement internal elastic conventions"
        }
    }
}


repositories {
    gradlePluginPortal()
}

dependencies {
    implementation("com.gradle.enterprise:com.gradle.enterprise.gradle.plugin:3.17.6")
    implementation("com.gradle:common-custom-user-data-gradle-plugin:2.0.2")
    implementation(project(":libs:utils"))
    implementation(project(":plugins:lifecycle"))
    implementation(project(":plugins:vault"))
    implementation(project(":plugins:cli:cli-lib"))
    implementation(project(":plugins:cli:jfrog"))
    implementation(project(":plugins:cli:manifest-tool"))
    implementation(project(":plugins:cli:shellcheck"))
    implementation(project(":plugins:cli:snyk"))
    implementation(project(":plugins:docker:base-image"))
    implementation(project(":plugins:docker:component-image"))

    integrationTestImplementation(project(":plugins:vault"))
    integrationTestImplementation(project(":plugins:cli:jfrog"))
    integrationTestImplementation(project(":plugins:cli:shellcheck"))
    integrationTestImplementation(project(":plugins:cli:snyk"))
    integrationTestImplementation(project(":plugins:cli:manifest-tool"))
}

tasks.integrationTest {
    // Need to validate these on a per OS and architecture basis
    inputs.properties("OS" to co.elastic.gradle.utils.OS.current())
    inputs.properties("Architecture" to co.elastic.gradle.utils.Architecture.current())
}