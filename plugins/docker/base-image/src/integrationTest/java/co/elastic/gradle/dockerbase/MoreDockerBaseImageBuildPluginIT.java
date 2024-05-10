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
package co.elastic.gradle.dockerbase;

import co.elastic.gradle.TestkitIntegrationTest;
import co.elastic.gradle.sandbox.SandboxDockerExecTask;
import co.elastic.gradle.utils.Architecture;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.squareup.okhttp.OkHttpClient;
import com.squareup.okhttp.Request;
import com.squareup.okhttp.Response;
import org.gradle.testkit.runner.BuildResult;
import org.gradle.testkit.runner.TaskOutcome;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Disabled;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Arrays;
import java.util.List;
import java.util.Objects;

import static co.elastic.gradle.AssertContains.assertContains;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

public class MoreDockerBaseImageBuildPluginIT extends TestkitIntegrationTest {

    @Test
    public void testPush() throws IOException {
        final Path ghTokenPath = Paths.get(System.getProperty("user.home") + "/.elastic/github.token");
        assumeTrue(
                Files.exists(ghTokenPath),
                "Test will be skipped unless a GH token is present at " + ghTokenPath
        );

        OkHttpClient client = new OkHttpClient();

        Request request = new Request.Builder()
                .url(" https://api.github.com/user")
                .header("Authorization", "token " + Files.readString(ghTokenPath).trim())
                .build();

        Response response = client.newCall(request).execute();
        String ghUsername = new ObjectMapper().readTree(response.body().string()).get("login").asText();


        helper.buildScript(String.format("""
                import java.net.URL
                plugins {
                   id("co.elastic.docker-base")
                   id("co.elastic.vault")
                }
                project.version = "myversion"
                vault {
                      address.set("https://vault-ci-prod.elastic.dev")
                      auth {
                        ghTokenFile()
                        ghTokenEnv()
                        tokenEnv()
                        roleAndSecretEnv()
                      }
                }
                val creds = vault.readAndCacheSecret("secret/ci/elastic-cloud/artifactory_creds").get()
                dockerBaseImage {
                    dockerTagPrefix.set("docker.elastic.co/employees/%s")
                    osPackageRepository.set(URL("https://${creds["username"]}:${creds["plaintext"]}@artifactory.elastic.dev/artifactory/gradle-plugins-os-packages"))
                    fromUbuntu("ubuntu", "20.04")
                }
                """, ghUsername
        ));
        Files.copy(
                Objects.requireNonNull(getClass().getResourceAsStream("/ubuntu.lockfile.yaml")),
                helper.projectDir().resolve("docker-base-image.lock")
        );
        final BuildResult result = gradleRunner
                .withArguments(
                        "--warning-mode", "fail", "-s", "-DallowInsecureRegistries=true", "dockerBaseImagePush"
                ).build();
        assertContains(
                result.getOutput(),
                String.format(
                        "Pushed image docker.elastic.co/employees/%s/%s-%s:myversion",
                        ghUsername, helper.projectDir().getFileName(), Architecture.current().dockerName()
                )
        );
    }

    @Test
    public void testTasksAreSkippedOnUnsupportedArch() throws IOException {
        final Architecture currentArchitecture = Architecture.current();
        final Architecture otherArchitecture = Arrays.stream(Architecture.values())
                .filter(value -> !currentArchitecture.equals(value))
                .findFirst()
                .orElseThrow(() -> new IllegalStateException("No architecture found"));

        helper.buildScript(String.format("""
                import %s.*
                import java.net.URL
                plugins {
                   id("co.elastic.docker-base")
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
                repositories {
                    mavenCentral()
                }
                val creds = vault.readAndCacheSecret("secret/ci/elastic-cloud/artifactory_creds").get()
                dockerBaseImage {
                    osPackageRepository.set(URL("https://${creds["username"]}:${creds["plaintext"]}@artifactory.elastic.dev/artifactory/gradle-plugins-os-packages"))
                    fromUbuntu("ubuntu", "20.04")
                    platforms.add(%s)
                }
                """, Architecture.class.getName(), otherArchitecture
        ));
        Files.copy(
                Objects.requireNonNull(getClass().getResourceAsStream("/ubuntu.lockfile.yaml")),
                helper.projectDir().resolve("docker-base-image.lock")
        );

        Assertions.assertEquals(
                TaskOutcome.SKIPPED,
                Objects.requireNonNull(
                        gradleRunner.withArguments("--warning-mode", "fail", "-s", "dockerBaseImageLockfile")
                                .build()
                                .task(":dockerBaseImageLockfile")
                ).getOutcome(),
                "Expected dockerBaseImageLockfile to be skipped but it was not "
        );

        final BuildResult result = gradleRunner.withArguments("--warning-mode", "fail", "-s",
                "dockerBasePull",
                "dockerBaseImageBuild",
                "dockerBaseImagePush"
        ).build();
        List.of(
                ":dockerBasePull", ":dockerBaseImageBuild", ":dockerBaseImagePush"
        ).forEach(task -> {
            Assertions.assertEquals(
                    TaskOutcome.SKIPPED,
                    Objects.requireNonNull(result.task(task)).getOutcome(),
                    "Expected " + task + " to be skipped but it was not "
            );
        });
    }

    @Test
    public void testMaxOutputSize() throws IOException {
        helper.buildScript("""
                import java.net.URL
                plugins {
                   id("co.elastic.docker-base")
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
                val creds = vault.readAndCacheSecret("secret/ci/elastic-cloud/artifactory_creds").get()
                dockerBaseImage {
                    osPackageRepository.set(URL("https://${creds["username"]}:${creds["plaintext"]}@artifactory.elastic.dev/artifactory/gradle-plugins-os-packages"))
                    fromUbuntu("ubuntu", "20.04")
                    maxOutputSizeMB.set(10)
                }
                """
        );
        Files.copy(
                Objects.requireNonNull(getClass().getResourceAsStream("/ubuntu.lockfile.yaml")),
                helper.projectDir().resolve("docker-base-image.lock")
        );
        final BuildResult result = gradleRunner.withArguments("--warning-mode", "fail", "-s",
                "dockerBaseImageBuild"
        ).buildAndFail();

        assertContains(result.getOutput(), "is greater than the current limit of 10Mb");
    }

    @Test
    public void testWithGeneratedInput() throws IOException {
        helper.buildScript("""
                import java.net.URL
                plugins {
                   id("co.elastic.docker-base")
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
                                
                val creds = vault.readAndCacheSecret("secret/ci/elastic-cloud/artifactory_creds").get()
                dockerBaseImage {
                    osPackageRepository.set(
                        URL("https://${creds["username"]}:${creds["plaintext"]}@artifactory.elastic.dev/artifactory/gradle-plugins-os-packages")
                    )                   
                    fromUbuntu("ubuntu", "20.04")
                    copySpec {
                        from(archive)
                        from(custom)
                        into("home")
                    }
                    run("find /home", "ls /home/my.zip", "ls /home/build.gradle.kts")
                }
                """
        );
        Files.copy(
                Objects.requireNonNull(getClass().getResourceAsStream("/ubuntu.lockfile.yaml")),
                helper.projectDir().resolve("docker-base-image.lock")
        );
        final BuildResult result = gradleRunner.withArguments("--warning-mode", "fail", "-s",
                "dockerBaseImageBuild"
        ).build();

        assertContains(result.getOutput(), "/home/my.zip");
    }

    @Test
    @Disabled("This test is centos specfic and currently broken")
    public void testAdditionalRepoConfig() {
        helper.buildScript("""
                import java.net.URL
                plugins {
                   id("co.elastic.docker-base")
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
                val creds = vault.readAndCacheSecret("secret/ci/elastic-cloud/artifactory_creds").get()
                dockerBaseImage {
                    osPackageRepository.set(URL("https://${creds["username"]}:${creds["plaintext"]}@artifactory.elastic.dev/artifactory/gradle-plugins-os-packages"))
                    fromCentos("centos", "7")
                    repoConfig("yum -y install epel-release")
                    install("jq")
                    run("jq --version")
                }
                """
        );

        final BuildResult Lockfileresult = gradleRunner.withArguments("--warning-mode", "fail", "-s",
                "dockerBaseImageLockfile"
        ).build();
        System.out.println(Lockfileresult.getOutput());

        final BuildResult result = gradleRunner.withArguments("--warning-mode", "fail", "-s",
                "dockerBaseImageBuild"
        ).build();

    }

    @Test
    public void testSandboxIntegration() throws IOException {
        helper.buildScript(String.format("""
                        import java.net.URL
                        import %s
                        plugins {
                           id("co.elastic.docker-base")
                           id("co.elastic.vault")
                           id("co.elastic.sandbox")
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
                        val creds = vault.readAndCacheSecret("secret/ci/elastic-cloud/artifactory_creds").get()
                        dockerBaseImage {
                            osPackageRepository.set(URL("https://${creds["username"]}:${creds["plaintext"]}@artifactory.elastic.dev/artifactory/gradle-plugins-os-packages"))
                            fromUbuntu("ubuntu", "20.04")
                            copySpec {
                               from(projectDir) {
                                  include("build.gradle.kts")
                               }
                            }
                        }
                        tasks.register<SandboxDockerExecTask>("test") {
                           image(project)
                           setCommandLine(listOf("grep", "SandboxDockerExecTask", "/build.gradle.kts"))
                        }
                        """,
                SandboxDockerExecTask.class.getName()
        ));
        Files.copy(
                Objects.requireNonNull(getClass().getResourceAsStream("/ubuntu.lockfile.yaml")),
                helper.projectDir().resolve("docker-base-image.lock")
        );
        final BuildResult result = gradleRunner.withArguments("--warning-mode", "fail", "-s",
                "test"
        ).build();

        assertContains(result.getOutput(), "tasks.register<SandboxDockerExecTask>(\"test\")");
    }


}
