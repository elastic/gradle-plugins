package co.elastic.gradle.cli.jfrog;

import co.elastic.gradle.TestkitIntegrationTest;
import org.gradle.testkit.runner.BuildResult;
import org.junit.jupiter.api.Test;

import static co.elastic.gradle.AssertContains.assertContains;
import static co.elastic.gradle.AssertFiles.assertPathExists;

public class JFrogPluginIT extends TestkitIntegrationTest {

    @Test
    void runJfrogCli() {
        helper.buildScript(String.format("""
                import %s
                plugins {
                   id("co.elastic.vault")
                   id("co.elastic.cli.jfrog")
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
                    jfrog {
                       val credentials = vault.readAndCacheSecret("secret/cloud-team/cloud-ci/artifactory_creds").get()
                       username.set(credentials["username"])
                       password.set(credentials["plaintext"])
                    }
                }
                                
                tasks.register<JFrogCliExecTask>("jfrog")
                """, JFrogCliExecTask.class.getName())
        );

        final BuildResult result = gradleRunner.withArguments("--warning-mode", "fail", "-s", "jfrog").build();

        assertContains(result.getOutput(), "2.16.4");

        assertPathExists(helper.projectDir().resolve("gradle/bin/jfrog-cli"));
        assertPathExists(helper.projectDir().resolve("gradle/bin/jfrog-cli-darwin-x86_64"));
        assertPathExists(helper.projectDir().resolve("gradle/bin/jfrog-cli-linux-x86_64"));
        assertPathExists(helper.projectDir().resolve("gradle/bin/jfrog-cli-linux-AARCH64"));
    }

}
