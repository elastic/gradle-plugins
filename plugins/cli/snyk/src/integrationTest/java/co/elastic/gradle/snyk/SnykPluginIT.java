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
                      address.set("https://secrets.elastic.co:8200")
                      auth {
                        ghTokenFile()
                        ghTokenEnv()
                        tokenEnv()
                        roleAndSecretEnv()
                      }
                }
                cli {
                    snyk {
                       val credentials = vault.readAndCacheSecret("secret/cloud-team/cloud-ci/artifactory_creds").get()
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

        assertContains(result.getOutput(), "[snyk] 1.856.0 (standalone)");

        assertPathExists(helper.projectDir().resolve("gradle/bin/snyk"));
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

        assertContains(result.getOutput(), "[snyk] 1.856.0 (standalone)");

        assertPathExists(helper.projectDir().resolve("gradle/bin/snyk"));
    }

}