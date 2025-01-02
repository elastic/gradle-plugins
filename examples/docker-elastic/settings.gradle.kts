import java.io.File

plugins {
    id("com.gradle.develocity").version("3.18.1")
    id("co.elastic.elastic-conventions").version(File("../../version-released").readText().trim())
}
