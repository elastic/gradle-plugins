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
package co.elastic.gradle.buildscan.xunit;

import co.elastic.gradle.TestkitIntegrationTest;
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
    public void xunitCreatorIntegration() {
        helper.buildScript("""
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
                """);

        final BuildResult result = gradleRunner.withArguments("--warning-mode", "fail", "-s", "test").build();
        Assertions.assertTrue(Files.exists(helper.projectDir().resolve("sample-produced.xml")));
        Assertions.assertEquals(TaskOutcome.SUCCESS, Objects.requireNonNull(result.task(":testxunitImport")).getOutcome());

        assertContains(result.getOutput(), "failure test name FAILED");
        assertContains(result.getOutput(), "4 tests completed, 2 failed, 1 skipped");
        assertContains(result.getOutput(), "8 tests completed, 4 failed, 2 skipped");

        final BuildResult secondRun = gradleRunner.withArguments("--warning-mode", "fail", "-s", "test").build();
        Assertions.assertEquals(TaskOutcome.UP_TO_DATE, Objects.requireNonNull(secondRun.task(":test")).getOutcome());
        Assertions.assertEquals(TaskOutcome.SKIPPED, Objects.requireNonNull(secondRun.task(":testxunitImport")).getOutcome());
    }

    @Test
    public void successfulTestWithoutOutput() {
        helper.writeFile("sample.xml", """
                <testsuite name="suite" tests="1" failures="0" time="0.01">
                    <testcase name="without output" classname="suite" time="0.01"/>
                </testsuite>
                """);
        helper.buildScript("""
                import co.elastic.gradle.buildscan.xunit.XUnitBuildScanImporterTask
                plugins {
                    id("co.elastic.build-scan.xunit")
                }
                tasks.register<XUnitBuildScanImporterTask>("testImport") {
                    from(file("sample.xml"))
                }
                """);

        final BuildResult result = gradleRunner.withArguments("--warning-mode", "fail", "testImport").build();
        Assertions.assertEquals(TaskOutcome.SUCCESS, Objects.requireNonNull(result.task(":testImport")).getOutcome());
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
