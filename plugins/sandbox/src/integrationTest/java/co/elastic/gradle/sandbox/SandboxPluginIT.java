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
package co.elastic.gradle.sandbox;

import co.elastic.gradle.TestkitIntegrationTest;
import co.elastic.gradle.utils.docker.ContainerImageProviderTask;
import org.gradle.testkit.runner.BuildResult;
import org.gradle.testkit.runner.TaskOutcome;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.Map;
import java.util.Objects;

import static co.elastic.gradle.AssertContains.assertContains;
import static co.elastic.gradle.AssertContains.assertDoesNotContain;
import static co.elastic.gradle.AssertFiles.assertPathExists;
import static org.junit.jupiter.api.Assertions.assertEquals;

@SuppressWarnings("NewClassNamingConvention")
class SandboxPluginIT extends TestkitIntegrationTest {

    @BeforeEach
    void setUp() {
        helper.writeScript(
                "scripts/test.sh",
                """
                        #!/bin/bash
                        echo Hello test!
                                                
                        find .
                                                
                        echo arguments are: $@
                                                
                        echo $(< file1 )
                        echo $(< dir/file2 )
                        echo $(< dir/sub-dir/file3 )
                                                
                        env
                                                
                        cd ..
                                                
                        mkdir -p build/script_out
                        echo "sandbox test" > build/script_out/output_file
                        mkdir -p build/script_out_dir/sub-dir/sub-dir
                        echo "sandbox test" >  build/script_out_dir/sub-dir/sub-dir/output_file
                                                                                                                                            
                        echo "Path is: $PATH" | sed "s#$PWD##"
                                                
                        find .
                        """
        );
        helper.writeFile("samples/file1", "Sample file1");
        helper.writeFile("samples/dir/file2", "Sample file2");
        helper.writeFile("samples/dir/sub-dir/file3", "Sample file3");

    }

    @Test
    void noSandboxTaskWithoutPlugin() {
        helper.buildScript(String.format("""
                import %s
                plugins {
                    id("co.elastic.sandbox").apply(false)
                }
                tasks.register<SandboxExecTask>("test")
                """, SandboxExecTask.class.getName()
        ));

        final BuildResult result = gradleRunner
                .withArguments("--warning-mode", "fail", "test")
                .buildAndFail();
        assertContains(result.getOutput(), "Task :test can only be used if the sandbox plugin is applied");
    }

    @Test
    void noDockerSandboxTaskWithoutPlugin() {
        helper.buildScript(String.format("""
                import %s
                plugins {
                    id("co.elastic.sandbox").apply(false)
                }
                tasks.register<SandboxDockerExecTask>("test")
                """, SandboxDockerExecTask.class.getName()
        ));

        final BuildResult result = gradleRunner
                .withArguments("--warning-mode", "fail", "test")
                .buildAndFail();
        assertContains(result.getOutput(), "Task :test can only be used if the sandbox plugin is applied");
    }

    @Test
    void taskWithNoArguments() {
        helper.buildScript(String.format("""
                import %s
                plugins {
                    id("co.elastic.sandbox")
                }
                tasks.register<SandboxDockerExecTask>("test")
                """, SandboxDockerExecTask.class.getName()
        ));

        final BuildResult result = gradleRunner
                .withArguments("--warning-mode", "fail", "-s", "test")
                .buildAndFail();
        assertContains(result.getOutput(), " property 'image' doesn't have a configured value");
    }

    @Test
    void sandboxExec() {
        helper.settings("""
                buildCache {
                    local {
                        directory = File(rootDir, "build-cache")
                    }
                }
                """
        );
        helper.buildScript(String.format("""
                import %s
                plugins {
                    id("co.elastic.sandbox")
                }
                tasks.register<SandboxExecTask>("test") {
                    setWorkingDir("samples")
                    setCommandLine(listOf("../scripts/test.sh", "arg1", "arg2"))
                    reads(file("scripts/test.sh"))
                    runsSystemBinary("mkdir")
                    runsSystemBinary("env")
                    runs(file("/usr/bin/find"))
                    runsSystemBinary("sed")
                    reads(file("samples/file1"))
                    reads(fileTree("samples/dir"))
                    writes(file("build/script_out/output_file"))
                    writes(fileTree("build/script_out_dir"))
                    environment("ENV_VAR1", "value1")
                    environment(mapOf("ENV_VAR2" to "value2"))
                }
                """, SandboxExecTask.class.getName()
        ));

        BuildResult result = runAndVerifyOutput();

        assertContains(result.getOutput(), "Path is: /.bin");
        // Env variable specific to docker utils
        assertContains(result.getOutput(), "DOCKER_BUILDKIT=1");

        final BuildResult upToDateResult = gradleRunner
                .withArguments("--warning-mode", "fail", "-s", "test")
                .build();

        assertEquals(TaskOutcome.UP_TO_DATE, Objects.requireNonNull(upToDateResult.task(":test")).getOutcome());
    }

    @Test
    void sandboxDockerExec() {
        helper.buildScript(String.format("""
                import %s
                plugins {
                    id("co.elastic.sandbox")
                }
                tasks.register<SandboxDockerExecTask>("test") {
                    image("ubuntu:20.04@sha256:8ae9bafbb64f63a50caab98fd3a5e37b3eb837a3e0780b78e5218e63193961f9")
                    setWorkingDir("samples")
                    setCommandLine(listOf("../scripts/test.sh", "arg1", "arg2"))
                    reads(file("scripts/test.sh"))
                    reads(file("samples/file1"))
                    reads(fileTree("samples/dir"))
                    writes(file("build/script_out/output_file"))
                    writes(fileTree("build/script_out_dir"))
                    environment("ENV_VAR1", "value1")
                    environment(mapOf("ENV_VAR2" to "value2"))
                }
                """, SandboxDockerExecTask.class.getName()
        ));

        BuildResult result = runAndVerifyOutput();
        assertContains(result.getOutput(), "PWD=/sandbox/samples");
        assertContains(result.getOutput(), "PATH=/usr/local/sbin:/usr/local/bin:/usr/sbin:/usr/bin:/sbin:/bin");

        final BuildResult upToDateResult = gradleRunner
                .withArguments("--warning-mode", "fail", "-s", "test")
                .build();

        assertEquals(Objects.requireNonNull(upToDateResult.task(":test")).getOutcome(), TaskOutcome.UP_TO_DATE);
    }

    private BuildResult runAndVerifyOutput() {
        final BuildResult result = gradleRunner
                .withArguments("--warning-mode", "fail", "-s", "test")
                .build();

        assertContains(result.getOutput(), "arguments are: arg1 arg2");
        assertContains(result.getOutput(), "Sample file1");
        assertContains(result.getOutput(), "Sample file2");
        assertContains(result.getOutput(), "Sample file3");
        assertContains(result.getOutput(), "ENV_VAR1=value1");
        assertContains(result.getOutput(), "ENV_VAR2=value2");

        assertPathExists(helper.projectDir().resolve("build/script_out/output_file"));
        assertPathExists(helper.projectDir().resolve("build/script_out_dir"));
        assertPathExists(helper.projectDir().resolve("build/script_out_dir/sub-dir/sub-dir/output_file"));

        return result;
    }

    @Test
    void testThatImagesArePulled() {
        helper.buildScript(String.format("""
                import %s
                plugins {
                    id("co.elastic.sandbox")
                    id("base")
                }
                                
                tasks.register<SandboxDockerExecTask>("test1") {
                    image("ubuntu:20.04@sha256:8ae9bafbb64f63a50caab98fd3a5e37b3eb837a3e0780b78e5218e63193961f9")
                    setCommandLine(listOf("uname", "-r", "-s"))
                }
                                
                tasks.register<SandboxDockerExecTask>("test2") {
                    image("ubuntu:18.04@sha256:42cd9143b6060261187a72716906187294b8b66653b50d70bc7a90ccade5c984")
                    setCommandLine(listOf("uname", "-r", "-s"))
                }
                                
                """, SandboxDockerExecTask.class.getName()
        ));

        final BuildResult result = gradleRunner
                .withArguments("--warning-mode", "fail", "-s", "test1", "test2")
                .build();

        assertContains(
                result.getOutput(),
                "Pulling docker image: ubuntu:20.04@sha256:8ae9bafbb64f63a50caab98fd3a5e37b3eb837a3e0780b78e5218e63193961f9"
        );
        assertContains(
                result.getOutput(),
                "Pulling docker image: ubuntu:18.04@sha256:42cd9143b6060261187a72716906187294b8b66653b50d70bc7a90ccade5c984"
        );

        final BuildResult upToDateResult = gradleRunner
                .withArguments("--warning-mode", "fail", "-s", "test1", "test2")
                .build();
        assertEquals(Objects.requireNonNull(upToDateResult.task(":resolveSandboxDockerDependencies")).getOutcome(), TaskOutcome.UP_TO_DATE);

        final BuildResult runResult = gradleRunner
                .withArguments("--warning-mode", "fail", "-s", "clean", "test1", "test2")
                .build();
        assertEquals(Objects.requireNonNull(runResult.task(":resolveSandboxDockerDependencies")).getOutcome(), TaskOutcome.SUCCESS);
    }

    @Test
    void testWithLocallyBuiltImage() {
        helper.settings("""
                include("subproject")
                """
        );

        helper.buildScript("subproject", String.format("""
                        import %s
                        abstract class BuildDockerImage : DefaultTask(), ContainerImageProviderTask {
                            init {
                                tag.convention("ubuntu:20.04@sha256:8ae9bafbb64f63a50caab98fd3a5e37b3eb837a3e0780b78e5218e63193961f9")
                                imageId.convention("test")
                            }
                            abstract override fun getTag(): Property<String>
                            abstract override fun getImageId(): Property<String>
                        }
                        val dockerTask by tasks.registering(BuildDockerImage::class)
                """, ContainerImageProviderTask.class.getName()
        ));
        helper.buildScript(String.format("""
                import %s
                import %s
                plugins {
                    id("co.elastic.sandbox")
                }
                                
                abstract class BuildDockerImage : DefaultTask(), ContainerImageProviderTask {
                    init {
                        tag.convention("ubuntu:20.04@sha256:8ae9bafbb64f63a50caab98fd3a5e37b3eb837a3e0780b78e5218e63193961f9")
                        imageId.convention("test")
                    }
                    abstract override fun getTag(): Property<String>
                    abstract override fun getImageId(): Property<String>
                }
                                
                val dockerTask by tasks.registering(BuildDockerImage::class)
                                
                tasks.register<SandboxDockerExecTask>("test1") {
                    image(dockerTask)
                    setCommandLine(listOf("uname", "-r", "-s"))
                }
                tasks.register<SandboxDockerExecTask>("test2") {
                    image(project(":subproject"))
                    setCommandLine(listOf("uname", "-r", "-s"))
                }
                """, SandboxDockerExecTask.class.getName(), ContainerImageProviderTask.class.getName()
        ));

        final BuildResult result = gradleRunner
                .withArguments("--warning-mode", "fail", "-s", "test1", "test2")
                .build();

        assertDoesNotContain(
                result.getOutput(),
                "Pulling docker image: ubuntu:20.04@sha256:8ae9bafbb64f63a50caab98fd3a5e37b3eb837a3e0780b78e5218e63193961f9"
        );
        assertContains(result.getOutput(), "Linux ");

        // Assert that the dependent tasks ran. These will be up-to-date as they don't have any task actions
        assertEquals(Objects.requireNonNull(result.task(":dockerTask")).getOutcome(), TaskOutcome.UP_TO_DATE);
        assertEquals(Objects.requireNonNull(result.task(":subproject:dockerTask")).getOutcome(), TaskOutcome.UP_TO_DATE);
    }

    @Test
    void retriesSandbox() {
        helper.writeScript(
                "scripts/test.sh",
                """
                        #!/bin/bash
                        echo Running try: $GRADLE_SANDBOX_TRY_NR!
                        if ! [ $GRADLE_SANDBOX_TRY_NR -eq 3 ] ; then
                            exit $GRADLE_SANDBOX_TRY_NR
                        fi
                        """
        );

        helper.buildScript(String.format("""
                import %s
                plugins {
                    id("co.elastic.sandbox")
                }
                tasks.register<SandboxExecTask>("test") {
                    setCommandLine(listOf("./scripts/test.sh", "arg1", "arg2"))
                    reads(file("scripts/test.sh"))
                    maxTries(3)
                }
                """, SandboxExecTask.class.getName()
        ));

        final BuildResult result = gradleRunner
                .withArguments("--warning-mode", "fail", "-s", "test")
                .build();

        assertDoesNotContain(result.getOutput(), "Running try: 0");
        assertContains(result.getOutput(), "Running try: 1");
        assertContains(result.getOutput(), "== Command failed on try 1, but 3 are allowed, going to retry ==");
        assertContains(result.getOutput(), "Running try: 2");
        assertContains(result.getOutput(), "== Command failed on try 2, but 3 are allowed, going to retry ==");
        assertContains(result.getOutput(), "Running try: 3");
    }

    @Test
    void retriesSandboxDoesNotExceedCount() {
        helper.writeScript(
                "scripts/test.sh",
                """
                        #!/bin/bash
                        echo Running try: $GRADLE_SANDBOX_TRY_NR!
                        exit $GRADLE_SANDBOX_TRY_NR
                        """
        );

        helper.buildScript(String.format("""
                import %s
                plugins {
                    id("co.elastic.sandbox")
                }
                tasks.register<SandboxExecTask>("test") {
                    setCommandLine(listOf("./scripts/test.sh", "arg1", "arg2"))
                    reads(file("scripts/test.sh"))
                    maxTries(3)
                }
                """, SandboxExecTask.class.getName()
        ));

        final BuildResult result = gradleRunner
                .withArguments("--warning-mode", "fail", "-s", "test")
                .buildAndFail();

        assertDoesNotContain(result.getOutput(), "Running try: 0");
        assertContains(result.getOutput(), "Running try: 1");
        assertContains(result.getOutput(), "== Command failed on try 1, but 3 are allowed, going to retry ==");
        assertContains(result.getOutput(), "Running try: 2");
        assertContains(result.getOutput(), "== Command failed on try 2, but 3 are allowed, going to retry ==");
        assertContains(result.getOutput(), "Running try: 3");
        assertContains(result.getOutput(), "Sandbox exec :test failed with exit code 3");
    }

    @Test
    void retriesDockerSandbox() {
        helper.writeScript(
                "scripts/test.sh",
                """
                        #!/bin/bash
                        echo Running try: $GRADLE_SANDBOX_TRY_NR!
                        if ! [ $GRADLE_SANDBOX_TRY_NR -eq 3 ] ; then
                            exit $GRADLE_SANDBOX_TRY_NR
                        fi
                        """
        );

        helper.buildScript(String.format("""
                import %s
                plugins {
                    id("co.elastic.sandbox")
                }
                tasks.register<SandboxDockerExecTask>("test") {
                    image("ubuntu:20.04@sha256:8ae9bafbb64f63a50caab98fd3a5e37b3eb837a3e0780b78e5218e63193961f9")
                    setCommandLine(listOf("./scripts/test.sh", "arg1", "arg2"))
                    reads(file("scripts/test.sh"))
                    maxTries(3)
                }
                """, SandboxDockerExecTask.class.getName()
        ));

        final BuildResult result = gradleRunner
                .withArguments("--warning-mode", "fail", "-s", "test")
                .build();

        assertDoesNotContain(result.getOutput(), "Running try: 0");
        assertContains(result.getOutput(), "Running try: 1");
        assertContains(result.getOutput(), "== Command failed on try 1, but 3 are allowed, going to retry ==");
        assertContains(result.getOutput(), "Running try: 2");
        assertContains(result.getOutput(), "== Command failed on try 2, but 3 are allowed, going to retry ==");
        assertContains(result.getOutput(), "Running try: 3");
    }

    @Test
    void sandboxExecCantEscapeSandbox() {
        helper.writeScript(
                "scripts/test.sh",
                """
                        #!/bin/bash
                        echo $(< samples/file1 )
                        echo $(< samples/dir/file2 )
                        echo $(< samples/dir/sub-dir/file3 )
                        """
        );
        helper.buildScript(String.format("""
                import %s
                plugins {
                    id("co.elastic.sandbox")
                }
                tasks.register<SandboxExecTask>("test") {
                    setCommandLine(listOf("./scripts/test.sh", "arg1", "arg2"))
                    reads(file("scripts/test.sh"))
                    // Don't declare reads, so sandbox can't see them
                    // reads(fileTree("samples"))
                    runsSystemBinary("mkdir")
                    runsSystemBinary("env")
                    runs(file("/usr/bin/find"))
                    runsSystemBinary("sed")
                }
                """, SandboxExecTask.class.getName()
        ));

        final BuildResult result = gradleRunner
                .withArguments("--warning-mode", "fail", "-s", "test")
                .withEnvironment(Map.of("ENV_VAR1", "value1"))
                .build();

        assertContains(result.getOutput(), "file1: No such file or directory");
        assertContains(result.getOutput(), "samples/dir/file2: No such file or directory");
        assertContains(result.getOutput(), "samples/dir/sub-dir/file3: No such file or directory");
        assertDoesNotContain(result.getOutput(), "ENV_VAR1=value1");
    }

    @Test
    void sandboxExecCantRunArbitraryCommands() {
        helper.buildScript(String.format("""
                import %s
                plugins {
                    id("co.elastic.sandbox")
                }
                tasks.register<SandboxExecTask>("test") {
                    setWorkingDir("samples")
                    setCommandLine(listOf("../scripts/test.sh", "arg1", "arg2"))
                    reads(file("scripts/test.sh"))
                    reads(file("samples/file1"))
                    reads(fileTree("samples/dir"))
                }
                """, SandboxExecTask.class.getName()
        ));

        final BuildResult result = gradleRunner
                .withArguments("--warning-mode", "fail", "-s", "test")
                .buildAndFail();

        assertContains(result.getOutput(), "env: command not found");
        assertContains(result.getOutput(), "mkdir: command not found");
        assertContains(result.getOutput(), "find: command not found");
        assertContains(result.getOutput(), "sed: command not found");
    }

    @Test
    void readsWithMap() {
        helper.buildScript(String.format("""
                import %s
                plugins {
                    id("co.elastic.sandbox")
                }
                tasks.register<SandboxExecTask>("test") {
                    setWorkingDir("samples")
                    setCommandLine(listOf("../scripts/test.sh", "arg1", "arg2"))
                    reads(file("scripts/test.sh"))
                    reads(mapOf(file("scripts/test.sh") to fileTree("samples")))
                    runsSystemBinary("sed", "env", "mkdir", "find")
                }
                """, SandboxExecTask.class.getName()
        ));

        final BuildResult result = gradleRunner
                .withArguments("--warning-mode", "fail", "-s", "test")
                .build();

        assertContains(result.getOutput(), "arguments are: arg1 arg2");
        assertContains(result.getOutput(), "Sample file1");
        assertContains(result.getOutput(), "Sample file2");
        assertContains(result.getOutput(), "Sample file3");

        helper.writeFile("samples/file1", "Change to be ignored!");

        final BuildResult upToDateResult = gradleRunner
                .withArguments("--warning-mode", "fail", "-s", "test")
                .build();

        assertEquals(Objects.requireNonNull(upToDateResult.task(":test")).getOutcome(), TaskOutcome.UP_TO_DATE);
    }

    @Test
    void multiProjectSupport() {
        helper.writeScript(
                "subproject/p1/test.sh",
                """
                        #!/bin/bash
                        echo Hello test!
                        find ..
                        cat ../p2/test_file
                        """
        );

        helper.writeFile("subproject/p2/test_file", "File from p2!");

        helper.settings("""
                include("subproject")
                include("subproject:p1")
                include("subproject:p2")
                """
        );

        helper.buildScript("subproject/p1", String.format("""
                import %s
                plugins {
                    id("co.elastic.sandbox")
                }
                tasks.register<SandboxExecTask>("test") {
                    setCommandLine(listOf("./test.sh"))
                    reads("test.sh")
                    reads(project(":subproject:p2").file("test_file"))
                    runsSystemBinary("cat", "find")
                }
                """,
                SandboxExecTask.class.getName()
        ));


        final BuildResult result = gradleRunner
                .withArguments("--warning-mode", "fail", "-s", "test")
                .build();

        assertContains(
                result.getOutput(),
                "File from p2!"
        );
    }

    @Test
    void worksWithLifecyclePlugin() {
        helper.buildScript(String.format("""
                import %s
                plugins {
                    id("co.elastic.sandbox")
                    id("co.elastic.lifecycle")
                }              
                """, SandboxExecTask.class.getName()
        ));

        final BuildResult result = gradleRunner
                .withArguments("--warning-mode", "fail", "-s", "resolveAllDependencies")
                .build();

        assertEquals(
                TaskOutcome.SUCCESS,
                Objects.requireNonNull(result.task(":resolveSandboxDockerDependencies")).getOutcome()
        );
    }


}