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
                      address.set("https://secrets.elastic.co:8200")
                      auth {
                        ghTokenFile()
                        ghTokenEnv()
                        tokenEnv()
                        roleAndSecretEnv()
                      }
                }
                cli {
                    manifestTool {
                       val credentials = vault.readAndCacheSecret("secret/cloud-team/cloud-ci/artifactory_creds").get()
                       username.set(credentials["username"])
                       password.set(credentials["plaintext"])
                    }
                }
                
                tasks.register<ManifestToolExecTask>("manifestTool")
                """, ManifestToolExecTask.class.getName())
        );

        final BuildResult result = gradleRunner.withArguments("--warning-mode", "fail", "-s", "manifestTool").build();

        assertContains(result.getOutput(), "[manifest-tool]    1.0.3 (commit: 505479b95ee682b7302a76e86f3b913d506ab3fc)");

        assertPathExists(helper.projectDir().resolve("gradle/bin/manifest-tool"));
        assertPathExists(helper.projectDir().resolve("gradle/bin/manifest-tool-darwin-x86_64"));
        assertPathExists(helper.projectDir().resolve("gradle/bin/manifest-tool-linux-x86_64"));
    }

    @Test
    void runWithExplicitParams() {
        helper.buildScript(String.format("""
                import %s
                plugins {
                   id("co.elastic.vault")
                   id("co.elastic.cli.manifest-tool")
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
                    manifestTool {
                       baseURL.set(java.net.URL("https://artifactory.elastic.dev/artifactory/github-release-proxy"))
                       version.set("v1.0.2")
                       val credentials = vault.readAndCacheSecret("secret/cloud-team/cloud-ci/artifactory_creds").get()
                       username.set(credentials["username"])
                       password.set(credentials["plaintext"])
                    }
                }
                
                tasks.register<ManifestToolExecTask>("manifestTool")
                """, ManifestToolExecTask.class.getName())
        );

        final BuildResult result = gradleRunner.withArguments("--warning-mode", "fail", "-s", "manifestTool").build();

        assertContains(result.getOutput(), "[manifest-tool]    1.0.2 (commit: fa20a3b9b43f7c1acedb8d97c249803cc923e009)");

        assertPathExists(helper.projectDir().resolve("gradle/bin/manifest-tool"));
    }

}