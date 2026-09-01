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
import co.elastic.gradle.utils.Architecture;
import com.squareup.okhttp.OkHttpClient;
import com.squareup.okhttp.Request;
import com.squareup.okhttp.Response;
import org.gradle.internal.impldep.com.fasterxml.jackson.databind.ObjectMapper;
import org.gradle.testkit.runner.BuildResult;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Arrays;
import java.util.Objects;

import static co.elastic.gradle.AssertContains.assertContains;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

public class DockerComponentImageBuildPluginRegistryIT extends TestkitIntegrationTest {

    private static Path ghTokenPath = Paths.get(System.getProperty("user.home") + "/.elastic/github.token");
    private static String ghHandle;

    @BeforeAll
    public static void onlyIfGHToken() throws IOException {
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

        ghHandle = new ObjectMapper().readTree(response.body().string()).get("login").asText();
    }

    @Test
    public void testPushAndScan() throws IOException {
        helper.settings("""
                     rootProject.name = "just-a-test"
                """);

        helper.buildScript(String.format("""
                import java.net.URL
                plugins {
                   id("co.elastic.docker-component")
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
                cli {
                    manifestTool {
                       val credentials = vault.readAndCacheSecret("secret/ci/elastic-gradle-plugins/artifactory_creds").get()
                       username.set(credentials["username"])
                       password.set(credentials["plaintext"])
                    }
                    snyk {
                       val credentials = vault.readAndCacheSecret("secret/ci/elastic-gradle-plugins/artifactory_creds").get()
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
                        dockerTagPrefix.set("docker.elastic.co/employees/%s")
                        from("ubuntu", "20.04")
                    }
                 
                }
                """, ghHandle
        ));

        Files.copy(
                Objects.requireNonNull(getClass().getResourceAsStream("/docker-component-image.lock")),
                helper.projectDir().resolve("docker-component-image.lock")
        );

        final BuildResult result = gradleRunner.withArguments("--warning-mode", "fail", "-s", "pushManifestList")
                .build();

        Arrays.stream(Architecture.values()).forEach(arch -> assertContains(
                result.getOutput(),
                String.format(
                        "Pushed image docker.elastic.co/employees/%s/just-a-test:myversion-%s",
                        ghHandle,  arch.dockerName()
                )
        ));
        assertContains(
                result.getOutput(),
                String.format(
                        "Pushed manifest list to docker.elastic.co/employees/%s/just-a-test:myversion",
                        ghHandle
                )
        );

        final BuildResult scanResult = gradleRunner.withArguments("--warning-mode", "fail", "-s", "dockerComponentImageScan")
                .build();
        assertContains(
                scanResult.getOutput(),
                "Monitoring docker.elastic.co/employees/alpar-t/just-a-test:myversion"
        );

    }

}
