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
package co.elastic.gradle.snyk;

import co.elastic.gradle.TestkitIntegrationTest;
import org.gradle.testkit.runner.BuildResult;
import org.junit.jupiter.api.Test;

import static co.elastic.gradle.AssertContains.assertContains;
import static co.elastic.gradle.AssertFiles.assertPathExists;

class SnykPluginIT extends TestkitIntegrationTest {

    @Test
    void runSnyk() {
        helper.buildScript(String.format("""
                import %s
                plugins {
                   id("co.elastic.vault")
                   id("co.elastic.cli.snyk")
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
                    snyk {
                       val credentials = vault.readAndCacheSecret("secret/ci/elastic-gradle-plugins/artifactory_creds").get()
                       username.set(credentials["username"])
                       password.set(credentials["plaintext"])
                    }
                }
                
                tasks.register<SnykCLIExecTask>("snyk") {
                   args = listOf("--version")
                }
                """, SnykCLIExecTask.class.getName())
        );

        final BuildResult result = gradleRunner.withArguments("--warning-mode", "fail", "-s", "snyk").build();

        assertContains(result.getOutput(), "[snyk] 1.1290.0");

        assertPathExists(helper.projectDir().resolve(".gradle/bin/snyk"));
    }

    @Test
    void runWithoutArtifactory() {
        helper.buildScript(String.format("""
                import %s
                plugins {
                   id("co.elastic.cli.snyk")
                }
                cli {
                    snyk {
                       baseURL.set(java.net.URL("https://static.snyk.io/"))
                    }
                }
                
                tasks.register<SnykCLIExecTask>("snyk") {
                   args = listOf("--version")
                }
                """, SnykCLIExecTask.class.getName())
        );

        final BuildResult result = gradleRunner.withArguments("--warning-mode", "fail", "-s", "snyk").build();

        assertContains(result.getOutput(), "[snyk] 1.1290.0");

        assertPathExists(helper.projectDir().resolve(".gradle/bin/snyk"));
    }

}