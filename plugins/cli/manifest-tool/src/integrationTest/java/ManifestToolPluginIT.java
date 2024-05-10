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
import co.elastic.gradle.TestkitIntegrationTest;
import co.elastic.gradle.cli.manifest.ManifestToolExecTask;
import org.gradle.testkit.runner.BuildResult;
import org.junit.jupiter.api.Test;

import static co.elastic.gradle.AssertContains.assertContains;
import static co.elastic.gradle.AssertFiles.assertPathExists;

class ManifestToolPluginIT extends TestkitIntegrationTest {

    @Test
    void runManifestTool() {
        helper.buildScript(String.format("""
                import %s
                plugins {
                   id("co.elastic.vault")
                   id("co.elastic.cli.manifest-tool")
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
                    manifestTool {
                       val credentials = vault.readAndCacheSecret("secret/ci/elastic-gradle-plugins/artifactory_creds").get()
                       username.set(credentials["username"])
                       password.set(credentials["plaintext"])
                    }
                }
                                
                tasks.register<ManifestToolExecTask>("manifestTool")
                """, ManifestToolExecTask.class.getName())
        );

        final BuildResult result = gradleRunner.withArguments("--warning-mode", "fail", "-s", "manifestTool").build();

        assertContains(result.getOutput(), "[manifest-tool]    2.1.6 (commit: d96ae95374f885e40b1e7de367c72ab09d7dc362)");

        assertPathExists(helper.projectDir().resolve(".gradle/bin/manifest-tool"));
        assertPathExists(helper.projectDir().resolve(".gradle/bin/manifest-tool-darwin-x86_64"));
        assertPathExists(helper.projectDir().resolve(".gradle/bin/manifest-tool-linux-x86_64"));
    }

    @Test
    void runWithExplicitVersion() {
        helper.buildScript(String.format("""
                import %s
                plugins {
                   id("co.elastic.vault")
                   id("co.elastic.cli.manifest-tool")
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
                    manifestTool {
                       baseURL.set(java.net.URL("https://artifactory.elastic.dev/artifactory/github-release-proxy"))
                       version.set("v1.0.2")
                       val credentials = vault.readAndCacheSecret("secret/ci/elastic-gradle-plugins/artifactory_creds").get()
                       username.set(credentials["username"])
                       password.set(credentials["plaintext"])
                    }
                }
                                
                tasks.register<ManifestToolExecTask>("manifestTool")
                """, ManifestToolExecTask.class.getName())
        );

        final BuildResult result = gradleRunner.withArguments("--warning-mode", "fail", "-s", "manifestTool").build();

        assertContains(result.getOutput(), "[manifest-tool]    1.0.2 (commit: fa20a3b9b43f7c1acedb8d97c249803cc923e009)");

        assertPathExists(helper.projectDir().resolve(".gradle/bin/manifest-tool"));
    }

}