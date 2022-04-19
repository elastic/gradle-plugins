dependencies {
    implementation(project(":libs:docker"))
    implementation(project(":libs:utils"))
    runtimeOnly("com.github.luben:zstd-jni:1.5.0-4")
    implementation("org.apache.commons:commons-compress:1.21")
    implementation("commons-io:commons-io:2.11.0")
}