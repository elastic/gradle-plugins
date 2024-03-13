import org.gradle.api.DefaultTask
import org.gradle.api.tasks.TaskAction
import java.io.File
import com.github.jk1.license.render.InventoryMarkdownReportRenderer
import org.gradle.internal.impldep.org.junit.experimental.categories.Categories.CategoryFilter.include

plugins {
    id("com.gradle.plugin-publish").version("1.2.1").apply(false)
    id("com.github.jk1.dependency-license-report").version("2.5")
}

allprojects {
    apply(plugin = "com.github.jk1.dependency-license-report")
    repositories {
        mavenCentral()
    }

    version = "0.0.1"
    group = "co.elastic.gradle"

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
