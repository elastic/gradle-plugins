package co.elastic.gradle.buildscan.xunit;

import co.elastic.gradle.TestkitIntegrationTest;
import co.elastic.gradle.sandbox.SandboxExecTask;
import org.apache.commons.io.IOUtils;
import org.gradle.testkit.runner.BuildResult;
import org.gradle.testkit.runner.TaskOutcome;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.util.Objects;

import static co.elastic.gradle.AssertContains.assertContains;

public class XUnitBuildScanImporterTaskIT extends TestkitIntegrationTest {

    @BeforeEach
    void setUp() throws IOException {
        IOUtils.copy(
                Objects.requireNonNull(getClass().getResource("/sample-jest-result.xml")).openStream(),
                new FileOutputStream(helper.projectDir().resolve("sample.xml").toFile())
        );
    }

    @Test
    public void sandboxIntegration()  {
        helper.buildScript(String.format("""
                import %s
                plugins {
                   id("co.elastic.build-scan.xunit")
                   id("co.elastic.sandbox")
                }
                
                val test by tasks.registering(SandboxExecTask::class) {
                    setCommandLine(listOf("cp", "-v", "sample.xml", "sample-produced.xml"))
                    runsSystemBinary("cp")
                    reads("sample.xml")
                    writes("sample-produced.xml")
                }
                """, SandboxExecTask.class.getName()
        ));


        final BuildResult result = gradleRunner.withArguments("--warning-mode", "fail", "-s", "test").build();
        Assertions.assertTrue(Files.exists(helper.projectDir().resolve("sample-produced.xml")));

        assertContains(result.getOutput(), "failure test name FAILED");
        assertContains(result.getOutput(), "4 tests completed, 2 failed, 1 skipped");
        assertContains(result.getOutput(), "8 tests completed, 4 failed, 2 skipped");
    }

    @Test
    public void standaloneFromFile()  {
        helper.buildScript(String.format("""
                import %s
                plugins {
                   id("co.elastic.build-scan.xunit")
                }
                
                tasks.register<XUnitBuildScanImporterTask>("tesImport") {
                   from(file("sample.xml"))
                }
                
                """, XUnitBuildScanImporterTask.class.getName()
        ));

        final BuildResult result = gradleRunner.withArguments("--warning-mode", "fail", "-s", "tesImport").build();

        assertContains(result.getOutput(), "failure test name FAILED");
        assertContains(result.getOutput(), "4 tests completed, 2 failed, 1 skipped");
        assertContains(result.getOutput(), "8 tests completed, 4 failed, 2 skipped");
    }

    @Test
    public void standaloneWithFileCollection()  {
        helper.buildScript(String.format("""
                import %s
                plugins {
                   id("co.elastic.build-scan.xunit")
                }
                
                tasks.register<XUnitBuildScanImporterTask>("tesImport") {
                   from(fileTree(projectDir).include("**/*.xml") as FileTree)
                }
                """, XUnitBuildScanImporterTask.class.getName()
        ));

        final BuildResult result = gradleRunner.withArguments("--warning-mode", "fail", "-s", "tesImport").build();

        assertContains(result.getOutput(), "failure test name FAILED");
        assertContains(result.getOutput(), "4 tests completed, 2 failed, 1 skipped");
        assertContains(result.getOutput(), "8 tests completed, 4 failed, 2 skipped");
    }

    @Test
    public void standaloneWithOtherTask()  {
        helper.buildScript(String.format("""
                import %s
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
                
                
                """, XUnitBuildScanImporterTask.class.getName()
        ));

        final BuildResult result = gradleRunner.withArguments("--warning-mode", "fail", "-s", "testImport").build();

        assertContains(result.getOutput(), "failure test name FAILED");
        assertContains(result.getOutput(), "4 tests completed, 2 failed, 1 skipped");
        assertContains(result.getOutput(), "8 tests completed, 4 failed, 2 skipped");

        final BuildResult result2 = gradleRunner.withArguments("--warning-mode", "fail", "-s", "test").build();

        Assertions.assertEquals(
                TaskOutcome.UP_TO_DATE,
                Objects.requireNonNull(result2.task(":testImport")).getOutcome()
        );
    }

}
