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
package co.elastic.gradle.cli.shellcheck;

import co.elastic.gradle.TestkitIntegrationTest;
import org.gradle.testkit.runner.BuildResult;
import org.gradle.testkit.runner.TaskOutcome;
import org.junit.jupiter.api.Test;

import java.util.Objects;

import static co.elastic.gradle.AssertFiles.assertPathExists;
import static org.gradle.internal.impldep.org.junit.Assert.assertEquals;

class ShellcheckPluginIT extends TestkitIntegrationTest {

    @Test
    void runShellcheck() {
        helper.writeFile("src/sample.sh", """
                #!/bin/bash
                echo $A
                if [[ $$(uname)  == "Linux" ]]
                                           then
                                             echo "Using Linux"
                                           fi
                """);

        helper.buildScript("""
                plugins {
                   id("co.elastic.lifecycle")
                   id("co.elastic.vault")
                   id("co.elastic.cli.shellcheck")
                }
                vault {
                      address.set("https://secrets.elastic.co:8200")
                      auth {
                        ghTokenFile()
                        ghTokenEnv()
                        tokenEnv()
                        roleAndSecretEnv()
                      }
                }
                cli {
                    shellcheck {
                       val credentials = vault.readAndCacheSecret("secret/cloud-team/cloud-ci/artifactory_creds").get()
                       username.set(credentials["username"])
                       password.set(credentials["plaintext"])
                    }
                }
                """
        );

        final BuildResult result = gradleRunner
                .withArguments("--warning-mode", "fail", "-s", "check")
                .buildAndFail();
        System.out.println(result.getOutput());
        assertPathExists(helper.projectDir().resolve("gradle/bin/shellcheck"));
        assertEquals(TaskOutcome.FAILED, Objects.requireNonNull(result.task(":shellcheck")).getOutcome());
    }

    @Test
    void runShellcheckWithSpecificTarget() {
        helper.writeFile("sample.sh", """
                #!/bin/bash
                
                echo "A nice script."
                """);

        helper.buildScript("""
                plugins {
                   id("co.elastic.vault")
                   id("co.elastic.cli.shellcheck")
                }
                vault {
                      address.set("https://secrets.elastic.co:8200")
                      auth {
                        ghTokenFile()
                        ghTokenEnv()
                        tokenEnv()
                        roleAndSecretEnv()
                      }
                }
                cli {
                    shellcheck {
                       val credentials = vault.readAndCacheSecret("secret/cloud-team/cloud-ci/artifactory_creds").get()
                       username.set(credentials["username"])
                       password.set(credentials["plaintext"])
                    }
                }
                tasks.shellcheck {
                   check(files("sample.sh"))
                }
                """
        );

        final BuildResult result = gradleRunner
                .withArguments("--warning-mode", "fail", "-s", "shellcheck")
                .build();
        System.out.println(result.getOutput());
        assertEquals(TaskOutcome.SUCCESS, Objects.requireNonNull(result.task(":shellcheck")).getOutcome());
        assertPathExists(helper.projectDir().resolve("gradle/bin/shellcheck"));

        final BuildResult resultUpToDate = gradleRunner
                .withArguments("-i", "shellcheck")
                .build();
        assertEquals(
                resultUpToDate.getOutput(),
                TaskOutcome.UP_TO_DATE, Objects.requireNonNull(resultUpToDate.task(":shellcheck")).getOutcome()
        );
    }

}