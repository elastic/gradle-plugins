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

```kotlin
import co.elastic.gradle.sandbox.SandboxExecTask

plugins {
    id("co.elastic.build-scan.xunit")
    id("co.elastic.sandbox")
}

tasks.register < SandboxExecTask::class > {
    setCommandLine(listOf("cp", "-v", "sample.xml", "sample-produced.xml"))
    runsSystemBinary("cp")
    reads("sample.xml")
    writes("sample-produced.xml")
}
```

The sandbox plugin integrates with tasks that implement the `XunitCreatorTask` interface, such as `SandboxExecTask` from
the [sandbox plugin](../sandbox/README.md). The plugin autoconfigures an import task for each xunit creator task. The
way this is set up also works with retries of the sandbox exec task so that if tests pass on a retry the tests that
initially failed will be marked as flaky.

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