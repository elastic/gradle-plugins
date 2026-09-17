XUnit Build Scan Import Plugin
==============================

About
-----

Integrates any task that produces xunit output with Gradle [Build Scans](https://scans.gradle.com/). Tests imported like
this will show up in the "test" section of the build scan. The same test can be imported multiple times. When this
happens the test is marked as `FLAKY` in Gradle Enterprise.

Importing the result makes it possible to utilize the full power of Gradle Enterprise of test result analysis,
reporting, and trend analysis.

**Note:** Due to how test execution is implemented in Gradle, there is an important **limitaion** of the plugin: The
timing reported is incorrect, thus Gradle Enterprise can't be used to find slow tests or otherwise reason about
execution times.

Usage
-----

### Integration with other plugins

The plugin automatically creates an import task for each task implementing
`co.elastic.gradle.utils.XunitCreatorTask`. The interface exposes the generated XML reports through
`getXunitFiles()`, a `Provider<Collection<File>>`. The import task runs as a finalizer when the producer did work.

```kotlin
import co.elastic.gradle.utils.XunitCreatorTask
import java.io.File

plugins {
    id("co.elastic.build-scan.xunit")
}

abstract class XunitReportTask : DefaultTask(), XunitCreatorTask {
    @get:InputFile
    abstract val sourceReport: RegularFileProperty

    @OutputFiles
    abstract override fun getXunitFiles(): Property<Collection<File>>

    @TaskAction
    fun generateReport() {
        sourceReport.get().asFile.copyTo(xunitFiles.get().single(), overwrite = true)
    }
}

tasks.register<XunitReportTask>("test") {
    sourceReport.set(layout.projectDirectory.file("sample.xml"))
    xunitFiles.set(listOf(layout.projectDirectory.file("sample-produced.xml").asFile))
}
```

This example copies an existing report to demonstrate automatic import. A test-running task can implement the
same interface to expose its own reports. Reports imported again after a retry mark initially failing tests as flaky.

## Standalone usage

Custom tasks to import the xml can also be created:
```kotlin
 import co.elastic.gradle.buildscan.xunit.XUnitBuildScanImporterTask

plugins {
    id("co.elastic.build-scan.xunit")
}

tasks.register<XUnitBuildScanImporterTask>("tesImport") {
    from(file("sample.xml"))
}
```

`FileCollection` ( e.g. using `files(...)`)  and `FileTree` (e.g. using `fileTree(...)`) ae also supported.
In case of setting up a custom task, one might also want to set up a proper relationship between the tasks
as the plugin doesn't have a way to do this automatically.
```kotlin
import co.elastic.gradle.buildscan.xunit.XUnitBuildScanImporterTask

plugins {
    id("co.elastic.build-scan.xunit")

}

tasks.register<XUnitBuildScanImporterTask>("testImport") {
    dependsOn("test")
    from(fileTree(projectDir).include("**/*.xml") as FileTree)
}

tasks.register<Exec>("test") {
    commandLine("cp", "sample.xml", "generated.xml")
    finalizedBy("testImport")
}
```