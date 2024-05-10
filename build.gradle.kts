import co.elastic.gradle.utils.OS
import java.io.File
import com.github.jk1.license.render.InventoryMarkdownReportRenderer
import co.elastic.gradle.utils.Architecture

plugins {
    id("com.gradle.plugin-publish").version("1.2.1").apply(false)
    id("com.github.jk1.dependency-license-report").version("2.5")

    // self-loop
    id("co.elastic.wrapper-provision-jdk").version("0.0.6")
    id("co.elastic.elastic-conventions").version("0.0.6")
}

allprojects {
    apply(plugin = "com.github.jk1.dependency-license-report")
    repositories {
        mavenCentral()
    }

    version = "0.0.7"
    group = "co.elastic.gradle"

    // Some projects are used for testing only, some are empty containers, everything else we publish
    if (! listOf(":", ":plugins", ":plugins:cli", ":plugins:docker", ":libs", ":libs:test-utils").contains(project.path)) {
        apply(plugin = "java-gradle-plugin")
        apply(plugin = "com.gradle.plugin-publish")

        configure<GradlePluginDevelopmentExtension> {
            website.set("https://github.com/elastic/gradle-plugins/blob/main/README.md")
            vcsUrl.set("https://github.com/elastic/gradle-plugins/")
            plugins.all {
                tags.addAll(listOf("elastic"))
            }
        }
    }

    if (extensions.findByType(JavaPluginExtension::class) != null) {
        configure<JavaPluginExtension> {
            toolchain {
                languageVersion.set(JavaLanguageVersion.of("17"))
            }
        }
    }

    licenseReport {
        outputDir = "$projectDir/licenses"
        renderers = arrayOf(InventoryMarkdownReportRenderer("licenses.md"))
    }

}

tasks.register("concatenateLicenseReports") {
    doLast {
        val noticeFile = File("$projectDir/NOTICE.txt")
        noticeFile.writeText("Elastic Gradle Plugins\n")
        noticeFile.appendText("Copyright 2012-2024 Elasticsearch B.V.\n\n")
        project.fileTree(project.rootDir) {
            include("**/licenses/licenses.md")
        }.forEach { licenseFile ->
            noticeFile.appendText("\n\n======================= ${licenseFile.parentFile.name} =======================\n\n")
            noticeFile.appendText(licenseFile.readText())
        }
    }
}

val licenseHeader = """
   /*
    * Copyright Elasticsearch B.V. and/or licensed to Elasticsearch B.V. under one
    * or more contributor license agreements. See the NOTICE file distributed with
    * this work for additional information regarding copyright
    * ownership. Elasticsearch B.V. licenses this file to you under
    * the Apache License, Version 2.0 (the "License"); you may
    * not use this file except in compliance with the License.
    * You may obtain a copy of the License at
    *
    *	http://www.apache.org/licenses/LICENSE-2.0
    *
    * Unless required by applicable law or agreed to in writing,
    * software distributed under the License is distributed on an
    * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
    * KIND, either express or implied.  See the License for the
    * specific language governing permissions and limitations
    * under the License.
    */
""".trimIndent()

tasks.register("addLicenseHeader") {
    group = "build"
    description = "Adds license header to Java files"

    doLast {
        addLicenseHeaderRecursively(projectDir)
    }
}

fun addLicenseHeaderRecursively(directory: File) {
    directory.listFiles()?.forEach { file ->
        if (file.isDirectory) {
            addLicenseHeaderRecursively(file)
        } else if (file.isFile && file.name.endsWith(".java")) {
            addLicenseHeader(file)
        }
    }
}

fun addLicenseHeader(file: File) {
    val content = file.readText()
    if (!content.startsWith("/*")) {
        file.writeText("$licenseHeader\n$content")
    }
}

tasks.wrapperProvisionJdk {
    javaReleaseName.set("17.0.10_7")
    checksums.set(
        mapOf(
            OS.LINUX to mapOf(
                Architecture.X86_64 to "a8fd07e1e97352e97e330beb20f1c6b351ba064ca7878e974c7d68b8a5c1b378",
                Architecture.AARCH64 to "6e4201abfb3b020c1fb899b7ac063083c271250bf081f3aa7e63d91291a90b74"
            ),
            OS.DARWIN to mapOf(
                Architecture.X86_64 to "e16ee89d3304bb2ba706f9a7b0ba279725c2aea55d5468336f8de4bb859f300d",
                Architecture.AARCH64 to "a6ec3b94f61695e8f445ee508411c56a2ce0cabc16ea4c4296ff062d13559d92"
            )
        )
    )
}