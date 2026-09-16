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

import com.sun.net.httpserver.HttpServer;
import org.gradle.api.GradleException;
import org.gradle.api.Project;
import org.gradle.api.plugins.ExtensionAware;
import org.gradle.api.provider.Provider;
import org.gradle.testfixtures.ProjectBuilder;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class VaultPluginTest {

    private Project testProject;

    @Test
    void concurrentProjectCacheMissesFetchOnce(@TempDir Path tempDir) throws Exception {
        final AtomicInteger requests = new AtomicInteger();
        final HttpServer server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/v1/kv/data/testing", exchange -> {
            requests.incrementAndGet();
            byte[] body = """
                    {"data":{"data":{"apikey":"fixture-key","organization":"fixture-org"},"metadata":{}},"lease_duration":3600,"renewable":false,"lease_id":""}
                    """.getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().set("Content-Type", "application/json");
            exchange.sendResponseHeaders(200, body.length);
            try (var output = exchange.getResponseBody()) {
                output.write(body);
            }
        });
        server.start();
        final var executor = Executors.newFixedThreadPool(8);
        try {
            Path token = tempDir.resolve("token");
            Files.writeString(token, "fixture-vault-token");
            List<Provider<Map<String, String>>> readers = new ArrayList<>();
            for (int index = 0; index < 8; index++) {
                Project project = ProjectBuilder.builder().withParent(testProject).withName("p" + index).build();
                project.getPluginManager().apply(VaultPlugin.class);
                VaultExtension vault = project.getExtensions().getByType(VaultExtension.class);
                vault.getAddress().set("http://127.0.0.1:" + server.getAddress().getPort());
                vault.auth(auth -> auth.tokenFile(token.toFile()));
                readers.add(vault.readAndCacheSecret("kv/testing", 2));
            }
            assertEquals(0, requests.get());
            final CountDownLatch start = new CountDownLatch(1);
            List<Future<Map<String, String>>> results = new ArrayList<>();
            for (var reader : readers) {
                results.add(executor.submit(() -> {
                    start.await();
                    return reader.get();
                }));
            }
            start.countDown();
            for (var result : results) {
                assertEquals(Map.of("apikey", "fixture-key", "organization", "fixture-org"), result.get(30, TimeUnit.SECONDS));
            }
            assertEquals(1, requests.get());
        } finally {
            executor.shutdownNow();
            server.stop(0);
        }
    }

    @BeforeEach
    void setUp(@TempDir Path tempDir) {
        testProject = ProjectBuilder.builder()
                .withProjectDir(tempDir.toFile())
                .build();
    }

    @SuppressWarnings("unchecked")
    @Test
    void pluginCanBeAppliedOnSettings() {
        testProject.getGradle().settingsEvaluated( settings -> settings.getPluginManager().apply(VaultPlugin.class));
    }

    @Test
    void pluginCanBeAppliedOnProject() {
        testProject.getPluginManager().apply(VaultPlugin.class);
    }

    @Test
    void githubCliCanBeConfigured() {
        testProject.getPluginManager().apply(VaultPlugin.class);
        final VaultExtension vault = testProject.getExtensions().getByType(VaultExtension.class);
        final VaultAuthenticationExtension authentication = ((ExtensionAware) vault).getExtensions()
                .getByType(VaultAuthenticationExtension.class);

        authentication.ghCli();

        assertInstanceOf(
                VaultAuthenticationExtension.GithubCli.class,
                authentication.getAuthMethods().get(0)
        );
    }

    @Test
    void githubCliReadsAndCachesToken(@TempDir Path tempDir) throws IOException {
        final Path executable = tempDir.resolve("gh");
        Files.writeString(executable, """
                #!/bin/sh
                printf 'called\\n' >> "$(dirname "$0")/calls"
                printf '%s\\n' "$@" > "$(dirname "$0")/arguments"
                printf 'value-from-cli\\n'
                """);
        assertTrue(executable.toFile().setExecutable(true));

        final VaultAuthenticationExtension extension = testProject.getObjects()
                .newInstance(VaultAuthenticationExtension.class);
        final VaultAuthenticationExtension.GithubCli githubCli = extension.new GithubCli(executable.toString());

        assertTrue(githubCli.isMethodUsable());
        assertEquals("Using GitHub token from `gh auth token`", githubCli.getExplanation());
        assertEquals("value-from-cli", githubCli.getToken().get());
        assertEquals(List.of("auth", "token"), Files.readAllLines(tempDir.resolve("arguments")));
        assertEquals(List.of("called"), Files.readAllLines(tempDir.resolve("calls")));
    }

    @Test
    void githubCliIsUnavailableWhenCommandCannotStart(@TempDir Path tempDir) {
        final VaultAuthenticationExtension extension = testProject.getObjects()
                .newInstance(VaultAuthenticationExtension.class);
        final VaultAuthenticationExtension.GithubCli githubCli = extension.new GithubCli(
                tempDir.resolve("does-not-exist").toString()
        );

        assertFalse(githubCli.isMethodUsable());
        assertEquals(
                "Tried to obtain a GitHub token using `gh auth token`, but the command could not be started",
                githubCli.getExplanation()
        );
    }

    @Test
    void offlineModeUsesExpiredCachedSecret() throws IOException {
        final Path cacheDir = testProject.getProjectDir().toPath().resolve(".gradle/secrets/secret/testing/v1");
        Files.createDirectories(cacheDir.resolve("data"));
        Files.writeString(cacheDir.resolve("leaseExpiration"), "0");
        Files.writeString(cacheDir.resolve("data/password"), "cached-value");
        testProject.getGradle().getStartParameter().setOffline(true);
        testProject.getPluginManager().apply(VaultPlugin.class);

        final VaultExtension vault = testProject.getExtensions().getByType(VaultExtension.class);

        assertEquals("cached-value", vault.readAndCacheSecret("secret/testing").get().get("password"));
    }

    @Test
    void offlineModeRejectsUncachedSecret() {
        testProject.getGradle().getStartParameter().setOffline(true);
        testProject.getPluginManager().apply(VaultPlugin.class);
        final VaultExtension vault = testProject.getExtensions().getByType(VaultExtension.class);

        final GradleException exception = assertThrows(
                GradleException.class,
                () -> vault.readAndCacheSecret("secret/testing").get()
        );

        assertEquals(
                "Cannot read Vault secret 'secret/testing' because Gradle is running with --offline and no cached value " +
                        "is available. Remove --offline to read the secret from Vault.",
                exception.getMessage()
        );
    }

    @Test
    void offlineModeRejectsUncachedRead() {
        testProject.getGradle().getStartParameter().setOffline(true);
        testProject.getPluginManager().apply(VaultPlugin.class);
        final VaultExtension vault = testProject.getExtensions().getByType(VaultExtension.class);

        final GradleException exception = assertThrows(
                GradleException.class,
                () -> vault.readSecret("secret/testing", 2).get()
        );

        assertEquals(
                "Cannot read Vault secret 'secret/testing' because Gradle is running with --offline. " +
                        "Remove --offline to read the secret from Vault.",
                exception.getMessage()
        );
    }
}
