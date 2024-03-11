gradlePlugin {
    plugins {
        testSourceSets(java.sourceSets.integrationTest.get())
        create("co.elastic.license-headers") {
            id = "co.elastic.license-headers"
            implementationClass = "co.elastic.gradle.license_headers.LicenseHeadersPlugin"
            displayName = "Elastic License Headers Plugin"
            description = "Check source files for elastic license headers"
        }
    }
}

dependencies {
    implementation(project(":libs:utils"))
    implementation(project(":plugins:lifecycle"))
}