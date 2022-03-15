plugins {
    `java-library`
}

dependencies {
    implementation(gradleApi())
    implementation("commons-io:commons-io:2.11.0")
    testImplementation("org.junit.jupiter:junit-jupiter:5.7.2")
}

tasks.test {
    useJUnitPlatform()
}