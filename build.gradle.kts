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
        val noticeFile = File("$projectDir/NOTICE")
        noticeFile.writeText("")
        project.fileTree(project.rootDir) {
            include("**/licenses/licenses.md")
        }.forEach { licenseFile ->
            noticeFile.appendText("\n\n======================= ${licenseFile.parentFile.name} =======================\n\n")
            noticeFile.appendText(licenseFile.readText())
        }
    }
}