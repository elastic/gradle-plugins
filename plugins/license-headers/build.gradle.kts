gradlePlugin {
    plugins {
        testSourceSets(java.sourceSets.integrationTest.get())
        create("co.elastic.license-headers") {
            id = "co.elastic.license-headers"
            implementationClass = "co.elastic.gradle.license_headers.LicenseHeadersPlugin"
        }
    }
}

dependencies {
    implementation(project(":libs:utils"))
    implementation(project(":plugins:lifecycle"))
}