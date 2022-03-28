dependencies {
    implementation("org.apache.commons:commons-compress:1.21")
    implementation("commons-io:commons-io:2.11.0")
    implementation(project(":libs:docker"))
    implementation(project(":libs:utils"))

    implementation(project(":plugins:docker:base"))

    val jacksonVersion = "2.13.2"
    implementation("com.fasterxml.jackson.core:jackson-databind:$jacksonVersion")
    implementation("com.fasterxml.jackson.dataformat:jackson-dataformat-yaml:$jacksonVersion")
    implementation("org.jetbrains:annotations:23.0.0")
    implementation("org.apache.commons:commons-csv:1.9.0")

    // Fixme: remove dependency from base image
    implementation("com.google.cloud.tools:jib-core:0.20.0")
}