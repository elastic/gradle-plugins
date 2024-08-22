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
package co.elastic.gradle.dockercomponent;

import co.elastic.gradle.TestkitIntegrationTest;
import co.elastic.gradle.sandbox.SandboxDockerExecTask;
import org.apache.commons.io.IOUtils;
import org.gradle.internal.impldep.org.testng.Assert;
import org.gradle.testkit.runner.BuildResult;
import org.gradle.testkit.runner.BuildTask;
import org.gradle.testkit.runner.TaskOutcome;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.attribute.PosixFilePermissions;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Stream;

import static co.elastic.gradle.AssertContains.assertContains;
import static org.junit.jupiter.api.Assertions.*;

public class DockerComponentPluginIT extends TestkitIntegrationTest {

    @Test
    public void buildLocalImportAndLocalScanFromStaticImage() throws IOException, InterruptedException {
        helper.settings("""
                     rootProject.name = "just-a-test"
                """);
        Files.copy(Objects.requireNonNull(getClass().getResourceAsStream("/test_created_image.sh")), helper.projectDir().resolve("test_created_image.sh"));
        helper.buildScript("""
                import kotlin.random.Random
                                
                plugins {
                       id("co.elastic.docker-component")
                       id("co.elastic.vault")
                }
                vault {
                          address.set("https://vault-ci-prod.elastic.dev")
                          auth {
                            ghTokenFile()
                            ghTokenEnv()
                            tokenEnv()
                            roleAndSecretEnv()
                          }
                }
                cli {
                     val credentials = vault.readAndCacheSecret("secret/ci/elastic-gradle-plugins/artifactory_creds").get()
                     snyk {                         
                           username.set(credentials["username"])
                           password.set(credentials["plaintext"])
                     }
                     manifestTool {                         
                           username.set(credentials["username"])
                           password.set(credentials["plaintext"])
                     }
                }
                tasks.withType<co.elastic.gradle.snyk.SnykCLIExecTask> {
                       environment(
                            "SNYK_TOKEN",
                            vault.readAndCacheSecret("secret/ci/elastic-gradle-plugins/snyk_api_key").get()["apikey"].toString()
                        )
                }
                dockerComponentImage {
                    buildAll {
                        from("ubuntu", "20.04")
                        maintainer("Jon Doe", "jon.doe@email.com")
                        copySpec("1000:1000") {
                            from(fileTree(projectDir)) {
                                include("build.gradle.kts")
                            }
                            into("home")
                        }
                        copySpec {
                           from("build.gradle.kts") {
                              into("home/${architecture.toString().toLowerCase()}")
                           }
                        }
                        copySpec {
                           from("test_created_image.sh")
                        }
                        entryPoint(listOf("/test_created_image.sh"))
                        cmd(listOf("foo", "bar"))
                        env("MY_FOO" to "BAR")
                        workDir("/home")
                        exposeTcp(80)
                        exposeUdp(80)
                        label("foo" to "bar")
                        changingLabel("random" to Random.nextInt(0, 10000).toString())
                    }
                }
                    """);
        Files.setPosixFilePermissions(helper.projectDir().resolve("test_created_image.sh"), PosixFilePermissions.fromString("r-xr-xr-x"));

        Files.copy(Objects.requireNonNull(getClass().getResourceAsStream("/docker-component-image.lock")), helper.projectDir().resolve("docker-component-image.lock"));

        final BuildResult pullResult = runGradleTask("dockerComponentPull");
        assertContains(pullResult.getOutput(), "Pulling base layers for ubuntu:20.04@sha256:a51c8bb81605567ea27d627425adf94a613d675a664bf473d43a55a8a26416b8 into the jib cache");
        assertContains(pullResult.getOutput(), "Pulling base layers for ubuntu:20.04@sha256:31cd7bbfd36421dfd338bceb36d803b3663c1bfa87dfe6af7ba764b5bf34de05 into the jib cache");

        Assert.assertTrue(Files.exists(Paths.get(System.getProperty("user.home")).resolve(".gradle-jib/cache")), "Job cache dir does not exist");

        Files.delete(helper.projectDir().resolve("docker-component-image.lock"));

        runGradleTask("dockerComponentLockFile");
        Assertions.assertTrue(Files.exists(helper.projectDir().resolve("docker-component-image.lock")));

        runGradleTask("dockerComponentImageBuild");
        Assertions.assertTrue(Files.exists(helper.projectDir().resolve("build/dockerComponentImageBuild/image-X86_64.tar.zstd")), "Image archive was not created at \"build/dockerComponentImageBuild/image-X86_64.tar.zstd\"");
        Assertions.assertTrue(Files.exists(helper.projectDir().resolve("build/dockerComponentImageBuild/image-AARCH64.tar.zstd")), "Image archive was not created at \"build/dockerComponentImageBuild/image-AARCH64.tar.zstd\"");


        final BuildResult result = runGradleTask("dockerComponentImageLocalImport");
        assertContains(result.getOutput(), "local/gradle-docker-component/just-a-test:latest");
        Assertions.assertTrue(getImagesInDaemon().contains("local/gradle-docker-component/just-a-test:latest"), "Expected image local/gradle-docker-component/just-a-test:latest to be available in the docker " + "daemon but it was not");

        final BuildResult secondResult = runGradleTask("dockerComponentImageLocalImport");
        Assert.assertEquals(TaskOutcome.UP_TO_DATE, Objects.requireNonNull(secondResult.task(":dockerComponentImageLocalImport")).getOutcome());

        final Process process = new ProcessBuilder().redirectOutput(ProcessBuilder.Redirect.PIPE).redirectError(ProcessBuilder.Redirect.PIPE).directory(helper.projectDir().toFile()).command("docker", "run", "--rm", "-t", "local/gradle-docker-component/just-a-test:latest").start();

        do {
            IOUtils.copy(process.getInputStream(), System.out);
            IOUtils.copy(process.getErrorStream(), System.err);
        } while (process.isAlive());


        final int exitCode = process.waitFor();
        if (exitCode != 0) {
            Assertions.fail("Verification script failed with exit code: " + exitCode);
        }
        System.out.println("Verification script completed successfully...");

        BuildResult scanResult = gradleRunner.withArguments("--warning-mode", "fail", "-s", "dockerComponentImageScanLocal").buildAndFail();

        assertContains(scanResult.getOutput(), "Docker image:");
        assertContains(scanResult.getOutput(), "local/gradle-docker-component/just-a-test:latest");

        runGradleTask("dockerComponentImageClean");

        Set<String> imagesInDaemonAfterClean = getImagesInDaemon();
        Assertions.assertFalse(
                getImagesInDaemon().contains("local/gradle-docker-component/just-a-test:latest"),
                "Expected image local/gradle-docker-component/just-a-test:latest to be cleaned from the docker " +
                        "daemon but it was not"
        );
    }

    @Test
    public void withMultiarchPlugin() throws IOException, InterruptedException {
        helper.settings("""
                     rootProject.name = "just-a-test"
                """);
        Files.copy(Objects.requireNonNull(getClass().getResourceAsStream("/test_created_image.sh")), helper.projectDir().resolve("test_created_image.sh"));
        helper.buildScript("""
                import kotlin.random.Random
                                
                plugins {
                       id("co.elastic.docker-component")
                       id("co.elastic.vault")
                       id("co.elastic.lifecycle-multi-arch")                       
                }
                vault {
                          address.set("https://vault-ci-prod.elastic.dev")
                          auth {
                            ghTokenFile()
                            ghTokenEnv()
                            tokenEnv()
                            roleAndSecretEnv()
                          }
                }
                cli {
                     val credentials = vault.readAndCacheSecret("secret/ci/elastic-gradle-plugins/artifactory_creds").get()
                     snyk {                         
                           username.set(credentials["username"])
                           password.set(credentials["plaintext"])
                     }
                     manifestTool {                         
                           username.set(credentials["username"])
                           password.set(credentials["plaintext"])
                     }
                }
                tasks.withType<co.elastic.gradle.snyk.SnykCLIExecTask> {
                       environment(
                            "SNYK_TOKEN",
                            vault.readAndCacheSecret("secret/ci/elastic-gradle-plugins/snyk_api_key").get()["apikey"].toString()
                        )
                }
                dockerComponentImage {
                    buildAll {
                        from("ubuntu", "20.04")
                        maintainer("Jon Doe", "jon.doe@email.com")
                        copySpec("1000:1000") {
                            from(fileTree(projectDir)) {
                                include("build.gradle.kts")
                            }
                            into("home")
                        }
                        copySpec {
                           from("build.gradle.kts") {
                              into("home/${architecture.toString().toLowerCase()}")
                           }
                        }
                        copySpec {
                           from("test_created_image.sh")
                        }
                        entryPoint(listOf("/test_created_image.sh"))
                        cmd(listOf("foo", "bar"))
                        env("MY_FOO" to "BAR")
                        workDir("/home")
                        exposeTcp(80)
                        exposeUdp(80)
                        label("foo" to "bar")
                        changingLabel("random" to Random.nextInt(0, 10000).toString())
                    }
                }
                    """);
        Files.copy(Objects.requireNonNull(getClass().getResourceAsStream("/docker-component-image.lock")), helper.projectDir().resolve("docker-component-image.lock"));

        final BuildResult buildPlatformIndependent = runGradleTask("buildPlatformIndependent");
        System.out.println(buildPlatformIndependent.getOutput());
        assertNull(buildPlatformIndependent.task(":dockerComponentImageBuild"), "Component image build should not run with `buildPlatformIndependent` but it did");

        final BuildResult buildForPlatform = runGradleTask("buildForPlatform");
        assertNull(buildForPlatform.task(":dockerComponentImageBuild"), "Component image build should not run with `buildForPlatform` but it did");

        final BuildResult buildCombinePlatform = runGradleTask("buildCombinePlatform");
        assertNotNull(buildCombinePlatform.task(":dockerComponentImageBuild"), "Component image build should run with `buildCombinePlatform` but it did not");
    }

    @Test
    public void testMaxOutputSize() throws IOException {
        helper.buildScript("""              
                plugins {
                       id("co.elastic.docker-component")
                }
                dockerComponentImage {
                    maxOutputSizeMB.set(1)
                    buildAll {
                        from("ubuntu", "20.04")
                    }
                }
                    
                """);

        Files.copy(Objects.requireNonNull(getClass().getResourceAsStream("/docker-component-image.lock")), helper.projectDir().resolve("docker-component-image.lock"));

        final BuildResult result = gradleRunner.withArguments("--warning-mode", "fail", "-s", "dockerComponentImageBuild").buildAndFail();

        assertContains(result.getOutput(), "is greater than the current limit of 1Mb");
    }


    @Test
    public void testDynamicallyGenerated() throws IOException {
        helper.buildScript("""
                plugins {
                       id("co.elastic.docker-component")
                }
                val archive by tasks.registering(Zip::class) {
                    from("build.gradle.kts")
                    archiveFileName.set("my.zip")
                    destinationDirectory.set(layout.buildDirectory.dir("dist"))
                }
                val custom by tasks.registering {
                    outputs.file("$buildDir/build.gradle.kts")
                    doLast {
                       copy {
                          from("build.gradle.kts")
                          into(buildDir)
                       }
                    }
                }
                dockerComponentImage {
                    buildAll {
                        from("ubuntu", "20.04")
                        copySpec {
                                from(archive)
                                from(custom)
                                into("home")
                        }
                    }
                }
                """);

        Files.copy(Objects.requireNonNull(getClass().getResourceAsStream("/docker-component-image.lock")), helper.projectDir().resolve("docker-component-image.lock"));

        final BuildResult result = runGradleTask("dockerComponentImageBuild");

        System.out.println(result.getOutput());

        final List<BuildTask> tasks = result.getTasks();
        final BuildTask custom = result.task(":custom");
        final BuildTask archive = result.task(":archive");
        final BuildTask dockerComponentImageBuild = result.task(":dockerComponentImageBuild");
        assertNotNull(custom, "Expected :custom task to run");
        assertNotNull(archive, "Expected :archive task to run");
        assertNotNull(dockerComponentImageBuild, "Expected :dockerComponentImageBuild task to run");
        assertTrue(tasks.indexOf(custom) < tasks.indexOf(dockerComponentImageBuild), "Expected :custom to run before the image build");
        assertTrue(tasks.indexOf(archive) < tasks.indexOf(dockerComponentImageBuild), "Expected :archive to run before the image build");
    }

    @Test
    public void testIntegrationWithSandbox() throws IOException {
        Files.copy(Objects.requireNonNull(getClass().getResourceAsStream("/docker-component-image.lock")), helper.projectDir().resolve("docker-component-image.lock"));
        helper.buildScript(String.format("""
                        import %s
                                        
                        plugins {
                               id("co.elastic.docker-component")                                  
                               id("co.elastic.sandbox")
                        }                               
                        dockerComponentImage {
                            buildAll {
                                from("ubuntu", "20.04")
                                maintainer("Jon Doe", "jon.doe@email.com")
                                copySpec {
                                    from(fileTree(projectDir)) {
                                        include("build.gradle.kts")
                                    }
                                }
                            }
                        }
                        
                        tasks.register<SandboxDockerExecTask>("test") {
                            image(project)
                            setCommandLine(listOf("grep", "SandboxDockerExecTask", "/build.gradle.kts"))
                        }
                """, SandboxDockerExecTask.class.getName()));

        final BuildResult result = gradleRunner.withArguments("--warning-mode", "fail", "-s", "test").build();

        assertContains(result.getOutput(), "tasks.register<SandboxDockerExecTask>(\"test\")");
    }

    private Set<String> getImagesInDaemon() throws IOException {
        final Process result = new ProcessBuilder().redirectOutput(ProcessBuilder.Redirect.PIPE).redirectError(ProcessBuilder.Redirect.PIPE).command("docker", "image", "ls", "--format", "{{.Repository}}:{{.Tag}}").start();
        Set<String> imagesInDaemon = new HashSet<>();
        do {
            IOUtils.copy(result.getErrorStream(), System.err);
        } while (result.isAlive());
        final BufferedReader lineReader = new BufferedReader(new InputStreamReader(result.getInputStream()));
        for (String line = lineReader.readLine(); line != null; line = lineReader.readLine()) {
            imagesInDaemon.add(line.trim());
        }
        return imagesInDaemon;
    }

    private BuildResult runGradleTask(String task) throws IOException {
        try {
            return gradleRunner.withArguments("--warning-mode", "fail", "-s", "-i", task).build();
        } finally {
            System.out.println("Listing of project dir:");
            Set<String> fileNamesOfInterest = Set.of("docker-component-image.lock");
            try (Stream<Path> s = Files.walk(helper.projectDir()).filter(each -> !each.toString().contains(".gradle"))) {
                s.forEach(each -> {
                    if (fileNamesOfInterest.contains(each.getFileName().toString())) {
                        System.out.println("Content of: " + helper.projectDir().relativize(each) + "\n");
                        try {
                            IOUtils.copy(Files.newInputStream(each), System.out);
                        } catch (IOException e) {
                            throw new UncheckedIOException(e);
                        }
                        System.out.println("\n");
                    } else {
                        System.out.println(helper.projectDir().relativize(each));
                    }
                });
            }
        }
    }
}
