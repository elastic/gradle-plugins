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
import co.elastic.gradle.cli.shellcheck.ShellcheckTask;
import co.elastic.gradle.elastic_conventions.ElasticConventionsPlugin;
import co.elastic.gradle.snyk.SnykCLIExecTask;
import co.elastic.gradle.vault.VaultExtension;
import org.gradle.testkit.runner.BuildResult;
import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.util.HashMap;
import java.util.Map;

import static co.elastic.gradle.AssertContains.assertContains;
import static co.elastic.gradle.AssertFiles.assertPathExists;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

public class ElasticConventionsPluginIT extends TestkitIntegrationTest {

    @Test
    public void vaultBackedSnykExplainsConfigurationCacheLimitation() throws java.io.IOException {
        final var cache = helper.projectDir().resolve(".gradle/secrets/kv/fixture/snyk_api_key/v2");
        Files.createDirectories(cache.resolve("data"));
        Files.writeString(cache.resolve("data/apikey"), "fixture-snyk-key");
        Files.writeString(cache.resolve("leaseExpiration"), "0");
        helper.buildScript("""
                import co.elastic.gradle.snyk.SnykCLIExecTask
                plugins {
                    id("co.elastic.elastic-conventions")
                }
                vault.engineVersion.set(2)
                tasks.register<SnykCLIExecTask>("scan") {
                    commandLine("/bin/sh", "-c", "test ${'$'}SNYK_TOKEN = fixture-snyk-key")
                }
                """);
        final BuildResult result = gradleRunner.withArguments(
                "--offline", "--configuration-cache", "-Pco.elastic.vault_prefix=kv/fixture", "scan"
        ).build();
        assertContains(result.getOutput(), "Configuration cache entry discarded");
    }

    @Test
    public void snykCredentialsAreReadOnlyWhenTheTaskExecutes() {
        helper.buildScript("""
                import co.elastic.gradle.snyk.SnykCLIExecTask
                plugins {
                    id("co.elastic.elastic-conventions")
                }
                vault.engineVersion.set(2)
                vault.address.set("http://127.0.0.1:1")
                val prepareSecret by tasks.registering {
                    doLast {
                        val cache = file(".gradle/secrets/kv/fixture/snyk_api_key/v2")
                        cache.resolve("data").mkdirs()
                        cache.resolve("data/apikey").writeText("fixture-snyk-key")
                        cache.resolve("leaseExpiration").writeText("0")
                    }
                }
                tasks.register<SnykCLIExecTask>("scan") {
                    dependsOn(prepareSecret)
                    commandLine("/bin/sh", "-c", "test ${'$'}SNYK_TOKEN = fixture-snyk-key")
                }
                tasks.register<SnykCLIExecTask>("skippedScan") {
                    onlyIf { false }
                    commandLine("/bin/sh", "-c", "exit 1")
                }
                """);

        final String cachePath = ".gradle/secrets/kv/fixture/snyk_api_key/v2/data/apikey";
        gradleRunner.withArguments("--offline", "-Pco.elastic.vault_prefix=kv/fixture", "help", "skippedScan").build();
        assertFalse(Files.exists(helper.projectDir().resolve(cachePath)));

        gradleRunner.withArguments("--offline", "-Pco.elastic.vault_prefix=kv/fixture", "scan").build();
        assertPathExists(helper.projectDir().resolve(cachePath));
    }

    @Test
    public void publishesBuildScan() {
        final String accessKey = System.getenv("DEVELOCITY_ACCESS_KEY");
        assumeTrue(accessKey != null && !accessKey.isBlank(),
                "Requires the Develocity access key injected by CI");

        helper.settings("""
                plugins {
                    id("co.elastic.elastic-conventions")
                }
                develocity.buildScan {
                    uploadInBackground.set(false)
                    buildScanPublished {
                        println("Conventions build scan published: ${buildScanUri}")
                    }
                }
                """);

        final BuildResult result = gradleRunner
                .withArguments("--scan", "--console=plain", "help")
                .build();

        assertContains(result.getOutput(),
                "Conventions build scan published: " + ElasticConventionsPlugin.DEVELOCITY_SERVER + "/s/");
        System.out.println(result.getOutput());
    }

    @Test
    public void withLifecycle() {
        helper.settings("""
                plugins {
                    id("co.elastic.elastic-conventions")
                }
                val remoteCache = buildCache.remote as com.gradle.develocity.agent.gradle.buildcache.DevelocityBuildCache
                logger.lifecycle("Develocity remote build cache enabled: ${remoteCache.isEnabled}")
                logger.lifecycle("Develocity remote build cache push: ${remoteCache.isPush}")
                """);
        helper.buildScript("""
                plugins {
                    id("co.elastic.elastic-conventions")
                }
                """);

        final BuildResult result = gradleRunner
                .withArguments("--warning-mode", "fail", "-s", "check")
                .build();
        assertContains(result.getOutput(), "Develocity remote build cache enabled: true");
        final boolean isCI = System.getenv("BUILD_URL") != null || System.getenv("BUILDKITE_BUILD_URL") != null;
        assertContains(result.getOutput(), "Develocity remote build cache push: " + isCI);
        System.out.println(result.getOutput());
    }

    @Test
    public void ciUsesInjectedDevelocityAccessKeyDuringBootstrap() {
        helper.settings("""
                plugins {
                    id("co.elastic.elastic-conventions")
                }
                val remoteCache = buildCache.remote as com.gradle.develocity.agent.gradle.buildcache.DevelocityBuildCache
                logger.lifecycle("Develocity remote build cache push: ${remoteCache.isPush}")
                """);

        final Map<String, String> environment = new HashMap<>(System.getenv());
        environment.put("BUILDKITE_BUILD_URL", "https://buildkite.example/build/1");
        environment.put("DEVELOCITY_ACCESS_KEY", "gradle-enterprise.elastic.co=test-access-key");

        final BuildResult result = gradleRunner
                .withEnvironment(environment)
                .withArguments("--warning-mode", "fail", "-s", "help")
                .build();

        assertContains(result.getOutput(), "Develocity remote build cache push: true");
    }

    @Test
    public void ciLoadsDevelocityAccessKeyFromVault() {
        final String secretPath = ElasticConventionsPlugin.DEVELOCITY_ACCESS_KEY_VAULT_PATH;
        final String cachedKeyPath = ".gradle/secrets/" + secretPath + "/v2/data/accesskey";
        assertFalse(Files.exists(helper.projectDir().resolve(cachedKeyPath)));

        helper.settings("""
                plugins {
                    id("co.elastic.elastic-conventions")
                }
                val accessKey = develocity.accessKey.get()
                check(accessKey.isNotBlank()) { "Vault fallback did not configure a Develocity access key" }
                check(file("%s").readText() == accessKey) {
                    "Develocity access key does not match the cached Vault secret"
                }
                val remoteCache = buildCache.remote as com.gradle.develocity.agent.gradle.buildcache.DevelocityBuildCache
                check(remoteCache.isEnabled && remoteCache.isPush) { "CI remote cache is not enabled for reads and writes" }
                logger.lifecycle("Develocity access key loaded from Vault successfully")
                """.formatted(cachedKeyPath));

        final Map<String, String> environment = new HashMap<>(System.getenv());
        environment.put("BUILDKITE_BUILD_URL", "https://buildkite.example/build/1");
        environment.remove("DEVELOCITY_ACCESS_KEY");
        environment.remove("DEVELOCITY_API_ACCESS_KEY");

        final BuildResult result = gradleRunner
                .withEnvironment(environment)
                .withArguments("--warning-mode", "fail", "-s", "help")
                .build();

        assertContains(result.getOutput(), "Reading " + secretPath + " from vault (cached value not available or expired)");
        assertContains(result.getOutput(), "Develocity access key loaded from Vault successfully");
        assertPathExists(helper.projectDir().resolve(cachedKeyPath));
    }

    @Test
    public void ciExplainsMissingDevelocityVaultGrant() {
        helper.settings("""
                plugins {
                    id("co.elastic.elastic-conventions")
                }
                """);

        final Map<String, String> environment = new HashMap<>(System.getenv());
        environment.put("BUILDKITE_BUILD_URL", "https://buildkite.example/build/1");
        environment.remove("DEVELOCITY_ACCESS_KEY");
        environment.remove("VAULT_TOKEN");
        environment.remove("VAULT_ROLE_ID");
        environment.remove("VAULT_SECRET_ID");
        environment.remove("VAULT_AUTH_GITHUB_TOKEN");

        final BuildResult result = gradleRunner
                .withEnvironment(environment)
                .withArguments(
                        "-Duser.home=" + helper.projectDir().resolve("empty-home"),
                        "--warning-mode", "fail", "-s", "help"
                )
                .buildAndFail();

        assertContains(result.getOutput(),
                "CI pipelines applying co.elastic.elastic-conventions must be granted read access to " +
                        "kv/ci-shared/develocity/* in Terrazzo"
        );
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
                   plugins {
                       id("co.elastic.elastic-conventions")
                       id("co.elastic.cli.jfrog")
                   }
                                  
                   val jfrog by tasks.registering(JFrogCliExecTask::class)
                   
                   tasks.check {
                      dependsOn(jfrog)
                   }
                 
                """, JFrogCliExecTask.class.getName())
        );

        final BuildResult result = gradleRunner
                .withArguments("--warning-mode", "fail", "-s", "check", "--refresh-dependencies", getVaultPrefixProperty())
                .build();

        System.out.println(result.getOutput());

        assertPathExists(helper.projectDir().resolve(".gradle/bin/jfrog-cli"));
        assertPathExists(helper.projectDir().resolve(".gradle/bin/jfrog-cli-darwin-x86_64"));
        assertPathExists(helper.projectDir().resolve(".gradle/bin/jfrog-cli-linux-x86_64"));
        assertPathExists(helper.projectDir().resolve(".gradle/bin/jfrog-cli-linux-aarch64"));

    }

    @Test
    public void errorMissingProperty() {
        helper.buildScript(String.format("""
                   import %s
                   plugins {
                       id("co.elastic.elastic-conventions")
                       id("co.elastic.cli.jfrog")
                   }
                                  
                   val jfrog by tasks.registering(JFrogCliExecTask::class)
                   
                   tasks.check {
                      dependsOn(jfrog)
                   }
                 
                """, JFrogCliExecTask.class.getName())
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
                plugins {                   
                    id("co.elastic.elastic-conventions")
                    id("co.elastic.cli.jfrog")
                }
                val jfrog by tasks.registering(JFrogCliExecTask::class)
               
                tasks.check {
                   dependsOn(jfrog)
                }
                
                """, JFrogCliExecTask.class.getName()
        ));
        helper.buildScript("p2", String.format("""
                import %s
                import %s
                import %s
                plugins {                   
                    id("co.elastic.cli.jfrog")
                    id("co.elastic.cli.snyk")
                    id("co.elastic.cli.shellcheck")
                    id("co.elastic.elastic-conventions")
                }
                val jfrog by tasks.registering(JFrogCliExecTask::class)
                val snyk by tasks.registering(SnykCLIExecTask::class)
                val shellCheck by tasks.registering(ShellcheckTask::class)
                tasks.check {
                  dependsOn(jfrog, snyk, shellCheck)
                }                
                """, JFrogCliExecTask.class.getName(), SnykCLIExecTask.class.getName(), ShellcheckTask.class.getName()
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

    }

    @Test
    public void withCliMultiProjectWithoutRoot() {
        helper.buildScript("p1", String.format("""
                import %s
                plugins {                   
                    id("co.elastic.elastic-conventions")
                    id("co.elastic.cli.jfrog")
                }
                val jfrog by tasks.registering(JFrogCliExecTask::class)
               
                tasks.check {
                   dependsOn(jfrog)
                }
                
                """, JFrogCliExecTask.class.getName()
        ));
        helper.buildScript("p2", String.format("""
                import %s
                import %s
                import %s
                plugins {                   
                    id("co.elastic.cli.jfrog")
                    id("co.elastic.cli.snyk")
                    id("co.elastic.cli.shellcheck")
                    id("co.elastic.elastic-conventions")
                }
                val jfrog by tasks.registering(JFrogCliExecTask::class)
                val snyk by tasks.registering(SnykCLIExecTask::class)
                val shellCheck by tasks.registering(ShellcheckTask::class)
                tasks.check {
                  dependsOn(jfrog, snyk, shellCheck)
                }                
                """, JFrogCliExecTask.class.getName(), SnykCLIExecTask.class.getName(), ShellcheckTask.class.getName()
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

    }

}
