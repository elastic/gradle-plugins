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
import org.gradle.testkit.runner.BuildResult;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static co.elastic.gradle.AssertContains.assertContains;

class VaultPluginOfflineIT extends TestkitIntegrationTest {

    @Test
    void offlineModeUsesExpiredCachedSecret() throws IOException {
        final Path cacheDir = helper.projectDir().resolve(".gradle/secrets/secret/testing/v1");
        Files.createDirectories(cacheDir.resolve("data"));
        Files.writeString(cacheDir.resolve("leaseExpiration"), "0");
        Files.writeString(cacheDir.resolve("data/top_secret"), "cached-password");
        helper.settings(String.format("""
                   import %s
                   rootProject.name = "integration-test"
                   plugins {
                       id("co.elastic.vault")
                   }
                   configure<VaultExtension> {
                      address.set("http://127.0.0.1:1/")
                   }
                   val vault = the<VaultExtension>()
                   logger.lifecycle("top_secret is {}", vault.readAndCacheSecret("secret/testing").get()["top_secret"])
                """, VaultExtension.class.getName()));

        final BuildResult result = gradleRunner
                .withArguments("--offline", "--warning-mode", "fail", "-s", "help")
                .build();

        assertContains(result.getOutput(), "top_secret is cached-password");
    }

    @Test
    void offlineModeRejectsUncachedSecret() {
        helper.settings(String.format("""
                   import %s
                   rootProject.name = "integration-test"
                   plugins {
                       id("co.elastic.vault")
                   }
                   configure<VaultExtension> {
                      address.set("http://127.0.0.1:1/")
                   }
                   val vault = the<VaultExtension>()
                   vault.readAndCacheSecret("secret/testing").get()
                """, VaultExtension.class.getName()));

        final BuildResult result = gradleRunner
                .withArguments("--offline", "--warning-mode", "fail", "-s", "help")
                .buildAndFail();

        assertContains(
                result.getOutput(),
                "Cannot read Vault secret 'secret/testing' because Gradle is running with --offline and no cached value is available"
        );
    }
}
