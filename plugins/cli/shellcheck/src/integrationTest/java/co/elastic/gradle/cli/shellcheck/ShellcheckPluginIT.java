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
                
                if [[ "$$(uname)" == "Linux" ]]
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

        final BuildResult result = gradleRunner.withArguments("--warning-mode", "fail", "-s", "check").buildAndFail();
        assertEquals(TaskOutcome.FAILED, Objects.requireNonNull(result.task(":shellcheck")).getOutcome());
        assertPathExists(helper.projectDir().resolve("gradle/bin/shellcheck"));
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
                       val credentials = vault.readSecret("secret/cloud-team/cloud-ci/artifactory_creds").get()
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