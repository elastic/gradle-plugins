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
package co.elastic.gradle.elatic_conventions;

import co.elastic.gradle.TestkitIntegrationTest;
import co.elastic.gradle.cli.jfrog.JFrogCliExecTask;
import co.elastic.gradle.cli.manifest.ManifestToolExecTask;
import co.elastic.gradle.cli.shellcheck.ShellcheckTask;
import co.elastic.gradle.elastic_conventions.ElasticConventionsPlugin;
import co.elastic.gradle.snyk.SnykCLIExecTask;
import co.elastic.gradle.vault.VaultExtension;
import org.gradle.testkit.runner.BuildResult;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.util.Objects;

import static co.elastic.gradle.AssertContains.assertContains;
import static co.elastic.gradle.AssertFiles.assertPathExists;

public class ElasticConventionsPluginIT extends TestkitIntegrationTest {

    @Test
    public void withLifecycle() {
        helper.settings("""
                plugins {
                    id("co.elastic.elastic-conventions")
                }
                """);
        helper.buildScript("""
                plugins {
                    id("co.elastic.elastic-conventions")
                }
                """);

        final BuildResult result = gradleRunner
                .withArguments("--warning-mode", "fail", "-s", "check")
                .build();
        System.out.println(result.getOutput());
    }

    @Test
    public void withVault() {
        helper.settings(String.format("""
                   import %s
                   plugins {
                       id("co.elastic.vault")
                       id("co.elastic.elastic-conventions")
                   }
                   
                   val vault = the<VaultExtension>()
                   logger.lifecycle("settings secret is {}", vault.readSecret("secret/ci/elastic-gradle-plugins/gradle-vault-integration").get()["key1"])
                   logger.lifecycle("settings secret cached is {}", vault.readAndCacheSecret("secret/ci/elastic-gradle-plugins/gradle-vault-integration").get()["key1"])
                """, VaultExtension.class.getName())
        );
        helper.buildScript(String.format("""
                   import %s
                   plugins {
                       id("co.elastic.vault")
                       id("co.elastic.elastic-conventions")
                   }
                                  
                   logger.lifecycle("build secret is {}", vault.readSecret("secret/ci/elastic-gradle-plugins/gradle-vault-integration").get()["key1"])
                   logger.lifecycle("build secret cached is {}", vault.readAndCacheSecret("secret/ci/elastic-gradle-plugins/gradle-vault-integration").get()["key1"])
                """, VaultExtension.class.getName())
        );

        final BuildResult result = gradleRunner
                .withArguments("--warning-mode", "fail", "-s", "help")
                .build();

        assertContains(result.getOutput(), "settings secret is test");
        assertContains(result.getOutput(), "settings secret cached is test");

        System.out.println(result.getOutput());
    }

    @Test
    public void withCli() {
        helper.buildScript(String.format("""
                   import %s
                   import %s
                   plugins {
                       id("co.elastic.elastic-conventions")
                       id("co.elastic.cli.jfrog")
                       id("co.elastic.cli.manifest-tool")
                   }
                                  
                   val jfrog by tasks.registering(JFrogCliExecTask::class)
                   val manifestTool by tasks.registering(ManifestToolExecTask::class)
                   
                   tasks.check {
                      dependsOn(jfrog, manifestTool)
                   }
                 
                """, JFrogCliExecTask.class.getName(), ManifestToolExecTask.class.getName())
        );

        final BuildResult result = gradleRunner
                .withArguments("--warning-mode", "fail", "-s", "check", "--refresh-dependencies", getVaultPrefixProperty())
                .build();

        System.out.println(result.getOutput());

        assertPathExists(helper.projectDir().resolve(".gradle/bin/jfrog-cli"));
        assertPathExists(helper.projectDir().resolve(".gradle/bin/jfrog-cli-darwin-x86_64"));
        assertPathExists(helper.projectDir().resolve(".gradle/bin/jfrog-cli-linux-x86_64"));
        assertPathExists(helper.projectDir().resolve(".gradle/bin/jfrog-cli-linux-aarch64"));

        assertPathExists(helper.projectDir().resolve(".gradle/bin/manifest-tool"));
        assertPathExists(helper.projectDir().resolve(".gradle/bin/manifest-tool-darwin-x86_64"));
        assertPathExists(helper.projectDir().resolve(".gradle/bin/manifest-tool-linux-x86_64"));
    }

    @Test
    public void errorMissingProperty() {
        helper.buildScript(String.format("""
                   import %s
                   import %s
                   plugins {
                       id("co.elastic.elastic-conventions")
                       id("co.elastic.cli.jfrog")
                       id("co.elastic.cli.manifest-tool")
                   }
                                  
                   val jfrog by tasks.registering(JFrogCliExecTask::class)
                   val manifestTool by tasks.registering(ManifestToolExecTask::class)
                   
                   tasks.check {
                      dependsOn(jfrog, manifestTool)
                   }
                 
                """, JFrogCliExecTask.class.getName(), ManifestToolExecTask.class.getName())
        );

        final BuildResult result = gradleRunner
                .withArguments("--warning-mode", "fail", "-s", "check", "--refresh-dependencies")
                .buildAndFail();

        assertContains(result.getOutput(),
                "This plugin requires the co.elastic.vault_prefix to be set for the vault integration. " +
                "Most of the time this needs to be set in the gradle.properties in your repo to `secret/ci/elastic-<name of your repo>`."
        );
    }

    protected static String getVaultPrefixProperty() {
        return "-P" + ElasticConventionsPlugin.PROPERTY_NAME_VAULT_PREFIX + "=secret/ci/elastic-gradle-plugins";
    }


    @Test
    public void withCliMultiProject() {
        helper.buildScript("""
        plugins {               
            id("co.elastic.elastic-conventions")
        }    
        """);
        helper.buildScript("p1", String.format("""
                import %s
                import %s
                plugins {                   
                    id("co.elastic.elastic-conventions")
                    id("co.elastic.cli.jfrog")
                    id("co.elastic.cli.manifest-tool")                    
                }
                val jfrog by tasks.registering(JFrogCliExecTask::class)
                val manifestTool by tasks.registering(ManifestToolExecTask::class)
               
                tasks.check {
                   dependsOn(jfrog, manifestTool)
                }
                
                """, JFrogCliExecTask.class.getName(), ManifestToolExecTask.class.getName()
        ));
        helper.buildScript("p2", String.format("""
                import %s
                import %s
                import %s
                import %s
                plugins {                   
                    id("co.elastic.cli.jfrog")
                    id("co.elastic.cli.manifest-tool")
                    id("co.elastic.cli.snyk")
                    id("co.elastic.cli.shellcheck")
                    id("co.elastic.elastic-conventions")
                }
                val jfrog by tasks.registering(JFrogCliExecTask::class)
                val manifestTool by tasks.registering(ManifestToolExecTask::class)
                val snyk by tasks.registering(SnykCLIExecTask::class)
                val shellCheck by tasks.registering(ShellcheckTask::class)
                tasks.check {
                  dependsOn(jfrog, manifestTool, snyk, shellCheck)
                }                
                """, JFrogCliExecTask.class.getName(), ManifestToolExecTask.class.getName(), SnykCLIExecTask.class.getName(), ShellcheckTask.class.getName()
        ));

        helper.settings("""
                      include("p1")
                      include("p2")
                      plugins {
                       id("co.elastic.elastic-conventions")
                      }                      
                  """);

        final BuildResult result = gradleRunner
                .withArguments("--warning-mode", "fail", "-s", "check", "--refresh-dependencies", getVaultPrefixProperty())
                .build();

        System.out.println(result.getOutput());

        assertPathExists(helper.projectDir().resolve(".gradle/bin/jfrog-cli"));
        assertPathExists(helper.projectDir().resolve(".gradle/bin/jfrog-cli-darwin-x86_64"));
        assertPathExists(helper.projectDir().resolve(".gradle/bin/jfrog-cli-linux-x86_64"));
        assertPathExists(helper.projectDir().resolve(".gradle/bin/jfrog-cli-linux-aarch64"));

        assertPathExists(helper.projectDir().resolve(".gradle/bin/manifest-tool"));
        assertPathExists(helper.projectDir().resolve(".gradle/bin/manifest-tool-darwin-x86_64"));
        assertPathExists(helper.projectDir().resolve(".gradle/bin/manifest-tool-linux-x86_64"));
    }

    @Test
    public void withCliMultiProjectWithoutRoot() {
        helper.buildScript("p1", String.format("""
                import %s
                import %s
                plugins {                   
                    id("co.elastic.elastic-conventions")
                    id("co.elastic.cli.jfrog")
                    id("co.elastic.cli.manifest-tool")                    
                }
                val jfrog by tasks.registering(JFrogCliExecTask::class)
                val manifestTool by tasks.registering(ManifestToolExecTask::class)
               
                tasks.check {
                   dependsOn(jfrog, manifestTool)
                }
                
                """, JFrogCliExecTask.class.getName(), ManifestToolExecTask.class.getName()
        ));
        helper.buildScript("p2", String.format("""
                import %s
                import %s
                import %s
                import %s
                plugins {                   
                    id("co.elastic.cli.jfrog")
                    id("co.elastic.cli.manifest-tool")
                    id("co.elastic.cli.snyk")
                    id("co.elastic.cli.shellcheck")
                    id("co.elastic.elastic-conventions")
                }
                val jfrog by tasks.registering(JFrogCliExecTask::class)
                val manifestTool by tasks.registering(ManifestToolExecTask::class)
                val snyk by tasks.registering(SnykCLIExecTask::class)
                val shellCheck by tasks.registering(ShellcheckTask::class)
                tasks.check {
                  dependsOn(jfrog, manifestTool, snyk, shellCheck)
                }                
                """, JFrogCliExecTask.class.getName(), ManifestToolExecTask.class.getName(), SnykCLIExecTask.class.getName(), ShellcheckTask.class.getName()
        ));

        helper.settings("""
                      include("p1")
                      include("p2")
                      plugins {
                       id("co.elastic.elastic-conventions")
                      }                      
                  """);

        final BuildResult result = gradleRunner
                .withArguments("--warning-mode", "fail", "-s", "check", "--refresh-dependencies", getVaultPrefixProperty())
                .build();

        System.out.println(result.getOutput());

        assertPathExists(helper.projectDir().resolve(".gradle/bin/jfrog-cli"));
        assertPathExists(helper.projectDir().resolve(".gradle/bin/jfrog-cli-darwin-x86_64"));
        assertPathExists(helper.projectDir().resolve(".gradle/bin/jfrog-cli-linux-x86_64"));
        assertPathExists(helper.projectDir().resolve(".gradle/bin/jfrog-cli-linux-aarch64"));

        assertPathExists(helper.projectDir().resolve(".gradle/bin/manifest-tool"));
        assertPathExists(helper.projectDir().resolve(".gradle/bin/manifest-tool-darwin-x86_64"));
        assertPathExists(helper.projectDir().resolve(".gradle/bin/manifest-tool-linux-x86_64"));
    }

    @Test
    public void withImageBuildAndLast() throws IOException {
        Files.copy(
                Objects.requireNonNull(getClass().getResourceAsStream("/ubuntu.lockfile.yaml")),
                helper.projectDir().resolve("docker-base-image.lock")
        );

        helper.buildScript("""
                   plugins {
                       id("co.elastic.docker-base")
                       id("co.elastic.docker-component")
                       id("co.elastic.elastic-conventions")
                   }
                   
                   dockerBaseImage {
                       fromUbuntu("ubuntu", "20.04")
                   }
                   dockerComponentImage {
                       buildAll {
                            from(project)
                       }
                   }
                """
        );

        final BuildResult scanResult = gradleRunner.withArguments("--warning-mode", "fail", "-S", "dockerComponentImageScanLocal", getVaultPrefixProperty())
                .buildAndFail();

        assertContains(scanResult.getOutput(), "[snyk] Tested ");

        gradleRunner.withArguments("--warning-mode", "fail", "-S", "resolveAllDependencies", getVaultPrefixProperty()).build();
    }

    @Test
    public void withImageBuildAndFirst() throws IOException {
        Files.copy(
                Objects.requireNonNull(getClass().getResourceAsStream("/ubuntu.lockfile.yaml")),
                helper.projectDir().resolve("docker-base-image.lock")
        );

        helper.buildScript("""
                   plugins {
                       id("co.elastic.elastic-conventions")
                       id("co.elastic.docker-base")
                       id("co.elastic.docker-component")                       
                   }
                   
                   dockerBaseImage {
                       fromUbuntu("ubuntu", "20.04")
                   }
                   dockerComponentImage {
                       buildAll {
                            from(project)
                       }
                   }
                """
        );

        final BuildResult scanResult = gradleRunner.withArguments("--warning-mode", "fail", "-S", "dockerComponentImageScanLocal", getVaultPrefixProperty())
                .buildAndFail();

        assertContains(scanResult.getOutput(), "[snyk] Tested ");

        gradleRunner.withArguments("--warning-mode", "fail", "-S", "resolveAllDependencies", getVaultPrefixProperty()).build();
    }

}
