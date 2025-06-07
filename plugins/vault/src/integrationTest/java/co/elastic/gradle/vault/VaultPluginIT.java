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
package co.elastic.gradle.vault;

import co.elastic.gradle.TestkitIntegrationTest;
import com.bettercloud.vault.Vault;
import com.bettercloud.vault.VaultConfig;
import com.bettercloud.vault.VaultException;
import org.gradle.internal.impldep.org.junit.ClassRule;
import org.gradle.testkit.runner.BuildResult;
import org.gradle.testkit.runner.TaskOutcome;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.testcontainers.vault.VaultContainer;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;

import static co.elastic.gradle.AssertContains.assertContains;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

public class VaultPluginIT extends TestkitIntegrationTest {

    @ClassRule
    public static VaultContainer vaultContainer = new VaultContainer<>("vault:1.1.3")
            .withVaultToken("my-root-token")
            .withSecretInVault("secret/testing", "top_secret=password1")
            .withSecretInVault("secret/testing2", "db_password=dbpassword1");

    @BeforeEach
    void setUpContainers() {
        if (!vaultContainer.isRunning()) {
            vaultContainer.start();
        }
    }

    @Test
    void // The code appears to be a test case method name in Java. It suggests that the method is
    // testing the functionality of reading vault secrets with a token and ensuring that they are
    // cached correctly.
    canReadVaultSecretsWithTokenAndTheyCacheCorrectly() {
        final var host = vaultContainer.getHost();
        final var firstMappedPort = vaultContainer.getFirstMappedPort();
        helper.settings(String.format("""
                   import %s
                   rootProject.name = "integration-test"
                   plugins {
                       id("co.elastic.vault")
                   }
                   configure<VaultExtension> {
                      engineVersion.set(2)
                      retries.set(2)
                      retryDelayMillis.set(1000)
                      address.set("http://%s:%s/")
                      auth {
                        tokenFile(file("no/such/token"))
                        roleAndSecretEnv("IT_IS","JUST_A", "LIE")
                        roleAndSecretEnv()
                        ghTokenEnv("SOME_GH_TOKEN")
                        ghTokenEnv()
                        ghTokenFile(file("theres/no/such/file"))
                        tokenEnv("MY_ENV_TOKEN")
                      }
                   }
                   val vault = the<VaultExtension>()
                   logger.lifecycle("top_secret is {}", vault.readSecret("secret/testing").get()["top_secret"])
                   logger.lifecycle("db_password is {}", vault.readAndCacheSecret("secret/testing2").get()["db_password"])
                """, VaultExtension.class.getName(), host, firstMappedPort));

        final BuildResult result = gradleRunner
                .withEnvironment(Collections.singletonMap("MY_ENV_TOKEN", "my-root-token"))
                .withArguments("--warning-mode", "fail", "-s", "help")
                .build();
        assertContains(result.getOutput(), "top_secret is password1");
        assertContains(result.getOutput(), "db_password is dbpassword1");
        assertCacheLocationExists(".gradle/secrets/secret/testing2");
        assertCacheLocationExists(".gradle/secrets/secret/testing2/leaseExpiration");
        assertCacheLocationExists(".gradle/secrets/secret/testing2/data");
        assertCacheLocationExists(".gradle/secrets/secret/testing2/data/db_password");
        assertCacheLocationDoesNotExists(".gradle/secrets/secret/testing/data/top_secret");
        assertCacheLocationDoesNotExists(".gradle/secrets/secret/testing2/data/top_secret");

        vaultContainer.stop();

        helper.settings(String.format("""
                   import %s
                   rootProject.name = "integration-test"
                   plugins {
                       id("co.elastic.vault")
                   }
                   configure<VaultExtension> {
                      engineVersion.set(2)
                      address.set("http://%s:%s/")
                      auth {
                        tokenEnv("MY_ENV_TOKEN")
                      }
                   }
                   val vault = the<VaultExtension>()
                   logger.lifecycle("db_password is {}", vault.readAndCacheSecret("secret/testing2").get()["db_password"])
                """, VaultExtension.class.getName(), host, firstMappedPort));

        final BuildResult result2 = gradleRunner
                .withEnvironment(Collections.singletonMap("MY_ENV_TOKEN", "my-root-token"))
                .withArguments("--warning-mode", "fail", "-s", "help")
                .build();
        // This should still work with vault stopped because it was cached
        assertContains(result2.getOutput(), "db_password is dbpassword1");
    }

    @Test
    void cacheAcrossContexts() {
        final var host = vaultContainer.getHost();
        final var firstMappedPort = vaultContainer.getFirstMappedPort();
        helper.settings(String.format("""
                           import %s
                           include("subproject")
                           plugins {
                               id("co.elastic.vault")
                           }
                           configure<VaultExtension> {
                              engineVersion.set(2)
                              retries.set(2)
                              retryDelayMillis.set(1000)
                              address.set("http://%s:%s/")
                              auth {
                                tokenFile(file("no/such/token"))
                                roleAndSecretEnv("IT_IS","JUST_A", "LIE")
                                roleAndSecretEnv()
                                ghTokenEnv("SOME_GH_TOKEN")
                                ghTokenEnv()
                                ghTokenFile(file("theres/no/such/file"))
                                tokenEnv("MY_ENV_TOKEN")
                              }
                           }
                           val vault = the<VaultExtension>()
                           logger.lifecycle("settings: db_password is {}", vault.readAndCacheSecret("secret/testing2").get()["db_password"])
                        """,
                VaultExtension.class.getName(), host, firstMappedPort)
        );

        helper.buildScript("""
                plugins {
                    id("co.elastic.vault")
                }
                vault {
                    address.set("http://127.0.0.1:8200/")
                    auth {
                     tokenEnv()
                    }
                }
                logger.lifecycle("root project: db_password is {}", vault.readAndCacheSecret("secret/testing2").get()["db_password"])
                """
        );
        helper.buildScript("subproject", """
                 plugins {
                    id("co.elastic.vault")
                }
                vault {
                    address.set("http://127.0.0.1:8200/")
                    auth {
                     tokenEnv()
                    }
                }
                logger.lifecycle("subproject: db_password is {}", vault.readAndCacheSecret("secret/testing2").get()["db_password"])
                """
        );

        final BuildResult result = gradleRunner
                .withEnvironment(Collections.singletonMap("MY_ENV_TOKEN", "my-root-token"))
                .withArguments("--warning-mode", "fail", "-s", "help")
                .build();
        assertContains(result.getOutput(), "settings: db_password is dbpassword1");
        assertContains(result.getOutput(), "root project: db_password is dbpassword1");
        assertContains(result.getOutput(), "subproject: db_password is dbpassword1");

        vaultContainer.stop();

        final BuildResult secondResult = gradleRunner
                .withEnvironment(Collections.singletonMap("MY_ENV_TOKEN", "my-root-token"))
                .withArguments("--warning-mode", "fail", "-s", "help")
                .build();
        assertContains(result.getOutput(), "settings: db_password is dbpassword1");
        assertContains(result.getOutput(), "root project: db_password is dbpassword1");
        assertContains(result.getOutput(), "subproject: db_password is dbpassword1");
        assertCacheLocationDoesNotExists("subproject/.gradle");
    }

    @Test
    void pluginWorksOnProjectTheSameWay() {
        final var host = vaultContainer.getHost();
        final var firstMappedPort = vaultContainer.getFirstMappedPort();
        helper.buildScript(String.format("""
                   plugins {
                       id("co.elastic.vault")
                   }
                   vault {
                      engineVersion.set(2)
                      address.set("http://%s:%s/")
                      auth {
                        tokenFile(file("no/such/token"))
                        roleAndSecretEnv("IT_IS","JUST_A", "LIE")
                        roleAndSecretEnv()
                        ghTokenEnv("SOME_GH_TOKEN")
                        ghTokenEnv()
                        ghTokenFile(file("theres/no/such/file"))
                        tokenEnv("MY_ENV_TOKEN")
                      }
                   }
                   logger.lifecycle("top_secret is {}", vault.readSecret("secret/testing").get()["top_secret"])
                   logger.lifecycle("db_password is {}", vault.readAndCacheSecret("secret/testing2").get()["db_password"])
                """, host, firstMappedPort));

        final BuildResult result = gradleRunner
                .withEnvironment(Collections.singletonMap("MY_ENV_TOKEN", "my-root-token"))
                .withArguments("--warning-mode", "fail", "-s", "help")
                .build();
        assertContains(result.getOutput(), "top_secret is password1");
        assertContains(result.getOutput(), "db_password is dbpassword1");
        assertCacheLocationExists(".gradle/secrets/secret/testing2");
        assertCacheLocationExists(".gradle/secrets/secret/testing2/leaseExpiration");
        assertCacheLocationExists(".gradle/secrets/secret/testing2/data");
        assertCacheLocationExists(".gradle/secrets/secret/testing2/data/db_password");
        assertCacheLocationDoesNotExists(".gradle/secrets/secret/testing/data/top_secret");
        assertCacheLocationDoesNotExists(".gradle/secrets/secret/testing2/data/top_secret");
    }

    @Test
    void canReadVaultSecretsWithRoles() throws VaultException {
        final var host = vaultContainer.getHost();
        final var firstMappedPort = vaultContainer.getFirstMappedPort();

        final Vault vault = new Vault(
                new VaultConfig()
                        .token("my-root-token")
                        .address("http://" + host + ":" + firstMappedPort)
                        .engineVersion(1)
                        .build()
        );

        // Enable app role auth
        vault.logical().write("sys/auth/approle", Map.of("type", "approle"));

        // Create a custom policy so we can access the secrets
        vault.logical().write(
                "sys/policy/custom_policy",
                Map.of("policy", """
                        path "secret/*" {
                          capabilities = ["create", "read", "update", "delete", "list"]
                        }
                        """)
        );

        // Create a role and secret ID for testing
        final Map<String, Object> data = new HashMap<>();
        data.put("secret_id_ttl", "10m");
        data.put("token_num_uses", 10);
        data.put("token_ttl", "20m");
        data.put("token_max_ttl", "30m");
        data.put("secret_id_num_uses", 40);
        data.put("policies", "custom_policy");
        vault.logical().write("auth/approle/role/my-role", data);
        final String roleId = vault.logical().read("auth/approle/role/my-role/role-id").getData().get("role_id");
        final String secret_id = vault.logical().write("auth/approle/role/my-role/secret-id", Collections.emptyMap()).getData().get("secret_id");

        helper.settings(String.format("""
                   import %s
                   rootProject.name = "integration-test"
                   plugins {
                       id("co.elastic.vault")
                   }
                   configure<VaultExtension> {
                      engineVersion.set(2)
                      address.set("http://%s:%s/")
                      auth {
                        roleAndSecretEnv()
                      }
                   }
                   val vault = the<VaultExtension>()
                   logger.lifecycle("top_secret is {}", vault.readSecret("secret/testing").get()["top_secret"])
                   logger.lifecycle("db_password is {}", vault.readAndCacheSecret("secret/testing2").get()["db_password"])
                """, VaultExtension.class.getName(), host, firstMappedPort));

        final BuildResult result = gradleRunner
                .withEnvironment(Map.of(
                        "VAULT_ROLE_ID", roleId,
                        "VAULT_SECRET_ID", secret_id,
                        "VAULT_AUTH_PATH", "approle"
                ))
                .withArguments("--warning-mode", "fail", "-s", "help")
                .build();
        assertContains(result.getOutput(), "top_secret is password1");
        assertContains(result.getOutput(), "db_password is dbpassword1");
        assertCacheLocationExists(".gradle/secrets/secret/testing2");
        assertCacheLocationExists(".gradle/secrets/secret/testing2/leaseExpiration");
        assertCacheLocationExists(".gradle/secrets/secret/testing2/data");
        assertCacheLocationExists(".gradle/secrets/secret/testing2/data/db_password");
        assertCacheLocationDoesNotExists(".gradle/secrets/secret/testing/data/top_secret");
        assertCacheLocationDoesNotExists(".gradle/secrets/secret/testing2/data/top_secret");

        // Run it again, this time without any credentials passed in, it should still work because the token is cached
        final BuildResult result2 = gradleRunner
                .withEnvironment(Collections.emptyMap())
                .withArguments("--warning-mode", "fail", "-s", "help")
                .build();
        System.out.println(result2.getOutput());
        assertContains(result2.getOutput(), "top_secret is password1");
        assertContains(result2.getOutput(), "db_password is dbpassword1");
    }

    @Test
    void noAuthConfiguredAndSecretRequested() {
        final var host = vaultContainer.getHost();
        final var firstMappedPort = vaultContainer.getFirstMappedPort();

        helper.settings(String.format("""
                   import %s
                   rootProject.name = "integration-test"
                   plugins {
                       id("co.elastic.vault")
                   }
                   configure<VaultExtension> {
                      engineVersion.set(2)
                      address.set("http://%s:%s/")
                   }
                   val vault = the<VaultExtension>()
                   logger.lifecycle("top_secret is {}", vault.readSecret("secret/testing").get()["top_secret"])
                """, VaultExtension.class.getName(), host, firstMappedPort));

        final BuildResult result = gradleRunner
                .withEnvironment(Collections.singletonMap("MY_ENV_TOKEN", "my-root-token"))
                .withArguments("--warning-mode", "fail", "-s", "help")
                .buildAndFail();
        assertContains(result.getOutput(), "No authentication configured to access");
    }

    @Test
    void noAuthConfiguredButSecretNotRequired() {
        helper.buildScript("""
                   plugins {
                       id("co.elastic.vault")
                   }
                   vault {
                      address.set("http://127.0.0.1:2222/")
                   }
                   if (! vault.isAuthAvailable) {
                      logger.lifecycle("No Vault authentication is available")
                   }
                   tasks.register("someTaskThatRequiresSecrets") {
                     doFirst {
                        logger.lifecycle("top_secret is {}", vault.readSecret("secret/testing").get()["top_secret"])
                     }
                     onlyIf { vault.isAuthAvailable }
                   }
                """);

        final BuildResult result = gradleRunner
                .withArguments("--warning-mode", "fail", "-s", "someTaskThatRequiresSecrets")
                .build();
        assertContains(result.getOutput(), "No Vault authentication is available");
        Assertions.assertEquals(
                TaskOutcome.SKIPPED,
                Objects.requireNonNull(result.task(":someTaskThatRequiresSecrets")).getOutcome()
        );
    }

    @Test
    void noAddressConfigured() {
        final var host = vaultContainer.getHost();
        final var firstMappedPort = vaultContainer.getFirstMappedPort();

        helper.settings(String.format("""
                   import %s
                   rootProject.name = "integration-test"
                   plugins {
                       id("co.elastic.vault")
                   }
                   val vault = the<VaultExtension>()
                   logger.lifecycle("top_secret is {}", vault.readSecret("secret/testing").get()["top_secret"])                  
                """, VaultExtension.class.getName(), host, firstMappedPort));

        final BuildResult result = gradleRunner
                .withEnvironment(Collections.singletonMap("MY_ENV_TOKEN", "my-root-token"))
                .withArguments("--warning-mode", "fail", "-s", "help")
                .buildAndFail();
        assertContains(result.getOutput(), "Cannot query the value of extension 'vault' property 'address' because it has no value available");
    }

    @Test
    void canGHAuthAndReadInfraVaultSecret() {
        final Path ghTokenPath = Paths.get(System.getProperty("user.home") + "/.elastic/github.token");
        assumeTrue(
                Files.exists(ghTokenPath),
                "Test will be skipped unless a GH token is present at " + ghTokenPath
        );
        helper.settings(String.format("""
                   import %s
                   rootProject.name = "integration-test"
                   plugins {
                       id("co.elastic.vault")
                   }
                   configure<VaultExtension> {
                      address.set("https://vault-ci-prod.elastic.dev")
                      auth {
                        ghTokenFile()
                      }
                   }
                   val vault = the<VaultExtension>()
                   logger.lifecycle("secret is {}", vault.readSecret("secret/ci/elastic-gradle-plugins/gradle-vault-integration").get()["key1"])
                   logger.lifecycle("secret cached is {}", vault.readAndCacheSecret("secret/ci/elastic-gradle-plugins/gradle-vault-integration").get()["key1"])
                """, VaultExtension.class.getName()));

        final BuildResult result = gradleRunner
                .withArguments("--warning-mode", "fail", "-s", "help")
                .build();

        assertContains(result.getOutput(), "secret is test");
        assertContains(result.getOutput(), "secret cached is test");
        assertCacheLocationExists(".gradle/secrets/secret/ci/elastic-gradle-plugins/gradle-vault-integration");
        assertCacheLocationExists(".gradle/secrets/secret/ci/elastic-gradle-plugins/gradle-vault-integration/leaseExpiration");
        assertCacheLocationExists(".gradle/secrets/secret/ci/elastic-gradle-plugins/gradle-vault-integration/data");
        assertCacheLocationExists(".gradle/secrets/secret/ci/elastic-gradle-plugins/gradle-vault-integration/data/key1");
    }

    private void assertCacheLocationExists(String other) {
        final Path secretsCacheDir = helper.projectDir().resolve(other);
        if (!Files.exists(secretsCacheDir)) {
            try {
                Assertions.fail(
                        "Expected secrets to cache to " + secretsCacheDir + " but the location does note exist.\n" +
                                "Contents of the project directory is:\n" + Files.walk(helper.projectDir())
                                .map(Object::toString)
                                .collect(Collectors.joining("\n"))
                );
            } catch (IOException e) {
                throw new UncheckedIOException(e);
            }
        }
    }

    private void assertCacheLocationDoesNotExists(String other) {
        final Path secretsCacheDir = helper.projectDir().resolve(other);
        if (Files.exists(secretsCacheDir)) {
            try {
                Assertions.fail(
                        "Expected secrets not to be cache to " + secretsCacheDir + " but they were regardless\n" +
                                "Contents of the directory is:\n" + Files.walk(helper.projectDir())
                                .map(Object::toString)
                                .collect(Collectors.joining("\n"))
                );
            } catch (IOException e) {
                throw new UncheckedIOException(e);
            }
        }
    }

}
