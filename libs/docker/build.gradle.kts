plugins {
    `java-library`
}

dependencies {
    implementation(gradleApi())
    implementation(project(":libs:utils"))
    implementation("commons-io:commons-io:2.11.0")
    testImplementation("org.junit.jupiter:junit-jupiter:5.7.2")
    testImplementation("org.mockito:mockito-all:1.10.19")
}

tasks.test {
    useJUnitPlatform()
}