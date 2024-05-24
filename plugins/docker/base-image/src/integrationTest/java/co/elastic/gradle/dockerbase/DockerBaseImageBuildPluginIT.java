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
import co.elastic.gradle.utils.Architecture;
import org.apache.commons.io.IOUtils;
import org.gradle.testkit.runner.BuildResult;
import org.gradle.testkit.runner.GradleRunner;
import org.gradle.testkit.runner.TaskOutcome;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Stream;

import static co.elastic.gradle.AssertContains.assertContains;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

public class DockerBaseImageBuildPluginIT extends TestkitIntegrationTest {

    @Test
    public void testMultiProject() throws IOException {
        helper.settings("""
                include("s1")
                include("s2")
                include("s3")
                """
        );
        helper.buildScript(String.format("""
                import java.net.URL
                import %s
                                
                                                            
                plugins {
                   id("co.elastic.vault")
                   id("co.elastic.cli.jfrog")
                   id("co.elastic.docker-base").apply(false)
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
                val creds = vault.readAndCacheSecret("secret/ci/elastic-gradle-plugins/artifactory_creds").get()
                
                cli {
                    jfrog {
                        username.set(creds["username"])
                        password.set(creds["plaintext"])
                    }
                }
                                
                subprojects {
                    apply(plugin = "co.elastic.docker-base")
                    print(project.name)
                    configure<BaseImageExtension> {
                        osPackageRepository.set(URL("https://${creds["username"]}:${creds["plaintext"]}@artifactory.elastic.dev/artifactory/gradle-plugins-os-packages"))  
                    }
                }
                """, BaseImageExtension.class.getName()
        ));

        helper.buildScript("s1", """
                plugins {
                   id("co.elastic.vault")
                   id("co.elastic.cli.jfrog")
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
                val creds = vault.readAndCacheSecret("secret/ci/elastic-gradle-plugins/artifactory_creds").get()
                cli {
                    jfrog {
                        username.set(creds["username"])
                        password.set(creds["plaintext"])
                    }
                }
                dockerBaseImage {
                  fromUbuntu("ubuntu", "20.04")
                  install("patch")
                }
                """
        );
        helper.buildScript("s2", """
                plugins {
                   id("co.elastic.vault")
                   id("co.elastic.cli.jfrog")
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
                val creds = vault.readAndCacheSecret("secret/ci/elastic-gradle-plugins/artifactory_creds").get()
                cli {
                    jfrog {
                        username.set(creds["username"])
                        password.set(creds["plaintext"])
                    }
                }
                dockerBaseImage {
                  from(project(":s1"))
                  run("patch --version")
                  install("curl")
                }
                """
        );
        helper.buildScript("s3", """
                plugins {
                   id("co.elastic.vault")
                   id("co.elastic.cli.jfrog")
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
                val creds = vault.readAndCacheSecret("secret/ci/elastic-gradle-plugins/artifactory_creds").get()
                cli {
                    jfrog {
                        username.set(creds["username"])
                        password.set(creds["plaintext"])
                    }
                }
                dockerBaseImage {
                  from(project(":s2"))
                  run("curl --version")
                }
                """
        );

        runGradleTask(gradleRunner, ":s1:dockerBaseImageLockfile");
        runGradleTask(gradleRunner, ":s2:dockerBaseImageLockfile");
        runGradleTask(gradleRunner, ":s3:dockerBaseImageLockfile");
        runGradleTask(gradleRunner, "dockerLocalImport");
    }

    @Test
    public void testPullTask() throws IOException {
        Files.createDirectories(helper.projectDir().resolve("s1"));
        Files.copy(
                Objects.requireNonNull(getClass().getResourceAsStream("/ubuntu.lockfile.yaml")),
                helper.projectDir().resolve("s1/docker-base-image.lock")
        );
        helper.settings("""
                include("s1")
                include("s2")
                """
        );
        helper.buildScript("s1", """
                plugins {
                    id("co.elastic.docker-base")
                }
                dockerBaseImage {
                      fromUbuntu("ubuntu", "20.04")
                }
                """
        );
        helper.buildScript("s2", """
                plugins {
                    id("co.elastic.docker-base")
                }
                dockerBaseImage {
                    from(project(":s1"))
                }
                """
        );
        final BuildResult result = gradleRunner.withArguments("--warning-mode", "fail", "-s", "dockerBasePull").build();
        assertContains(result.getOutput(), "Pulling from library/ubuntu");
        assertContains(
                result.getOutput(),
                Architecture.current().map(Map.of(
                        Architecture.AARCH64, "sha256:9bfe2c7a24b46c861ffea8b27dd1015e3b52e93e5581a09eacecd5a3cd601924",
                        Architecture.X86_64, "sha256:cc9cc8169c9517ae035cf293b15f06922cb8c6c864d625a72b7b18667f264b70"
                ))
        );
        assertEquals(TaskOutcome.SKIPPED, Objects.requireNonNull(result.task(":s2:dockerBasePull")).getOutcome());
    }

    @Test
    public void testDockerEphemeralConfig() throws IOException {
        helper.buildScript("""
                import java.net.URL
                plugins {
                   id("co.elastic.docker-base")
                   id("co.elastic.vault")
                   id("co.elastic.cli.jfrog")
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
                dependencies {
                   dockerEphemeral("org.slf4j:slf4j-api:1.7.36")
                }
                val creds = vault.readAndCacheSecret("secret/ci/elastic-gradle-plugins/artifactory_creds").get()
                cli {
                    jfrog {
                        username.set(creds["username"])
                        password.set(creds["plaintext"])
                    }
                }
                dockerBaseImage {
                    osPackageRepository.set(URL("https://${creds["username"]}:${creds["plaintext"]}@artifactory.elastic.dev/artifactory/gradle-plugins-os-packages"))
                    fromUbuntu("ubuntu", "20.04")
                    run(
                        "ls $dockerEphemeral/slf4j-api-1.7.36.jar",
                    )
                }
                """
        );
        Files.copy(
                Objects.requireNonNull(getClass().getResourceAsStream("/ubuntu.lockfile.yaml")),
                helper.projectDir().resolve("docker-base-image.lock")
        );
        final BuildResult result = gradleRunner.withArguments("--warning-mode", "fail", "-s", "dockerBaseImageBuild").build();
        assertContains(result.getOutput(), "slf4j-api-1.7.36.jar");
    }

    private BuildResult runGradleTask(GradleRunner gradleRunner, String task) throws IOException {
        try {
            return gradleRunner.withArguments("--warning-mode", "fail", "-s", task).build();
        } finally {
            System.out.println("Listing of project dir:");
            Set<String> fileNamesOfInterest = Set.of("docker-base-image.lock", "Dockerfile", ".dockerignore", "gradle-configuration.list");
            try (Stream<Path> s = Files.walk(helper.projectDir()).filter(each -> !each.toString().contains(".gradle"))) {
                s.forEach(each -> {
                    if (fileNamesOfInterest.contains(each.getFileName().toString())) {
                        System.out.println("Content of: " + helper.projectDir().relativize(each) + "\n");
                        try {
                            IOUtils.copy(Files.newInputStream(each), System.out);
                        } catch (IOException e) {
                            throw new UncheckedIOException(e);
                        }
                        System.out.println("\n----\n");
                    } else {
                        System.out.println(helper.projectDir().relativize(each));
                    }
                });
            }
            System.out.println("Done Listing of project dir");
        }
    }

    @Test
    public void testLockfileWithEmulation() throws IOException {
        assumeTrue(
                Architecture.current().equals(Architecture.AARCH64),
                "Test will be skipped unless running on ARM"
        );
        helper.buildScript("""
        import java.net.URL
            plugins {
               id("co.elastic.docker-base")
               id("co.elastic.cli.jfrog")
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
            val creds = vault.readAndCacheSecret("secret/ci/elastic-gradle-plugins/artifactory_creds").get()
            cli {
                jfrog {
                    username.set(creds["username"])
                    password.set(creds["plaintext"])
                }
            }
            dockerBaseImage {
                osPackageRepository.set(URL("https://${creds["username"]}:${creds["plaintext"]}@artifactory.elastic.dev/artifactory/gradle-plugins-os-packages"))
                fromUbuntu("ubuntu", "20.04")
                install("patch")
            }
        """);
        final BuildResult result = runGradleTask(gradleRunner, "dockerBaseImageLockfileAllWithEmulation");
        System.out.println(result.getOutput());
        assertNotNull(result.task(":dockerBaseImageLockfile"), "Expected task dockerBaseImageLockfile to have run");
        switch (Architecture.current()) {
            case X86_64 -> {
                assertNotNull(result.task(":dockerBaseImageLockfilearm64"), "Expected task dockerBaseImageLockfilearm64 to have run.");
            }
            case AARCH64 -> {
                assertNotNull(result.task(":dockerBaseImageLockfileamd64"), "Expected task dockerBaseImageLockfileamd64 to have run.");
            }
        }
    }

}
