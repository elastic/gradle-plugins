import java.io.File

plugins {
    id("com.gradle.enterprise").version("3.9")
    id("co.elastic.elastic-conventions").version(File("../../version-released").readText().trim())
}