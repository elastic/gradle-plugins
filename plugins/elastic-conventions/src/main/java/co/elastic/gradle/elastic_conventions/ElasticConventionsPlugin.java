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
package co.elastic.gradle.elastic_conventions;

import co.elastic.gradle.cli.base.BaseCLiExtension;
import co.elastic.gradle.cli.base.BaseCliPlugin;
import co.elastic.gradle.cli.base.CliExtension;
import co.elastic.gradle.dockerbase.BaseImageExtension;
import co.elastic.gradle.dockerbase.DockerBaseImageBuildPlugin;
import co.elastic.gradle.lifecycle.LifecyclePlugin;
import co.elastic.gradle.snyk.SnykCLIExecTask;
import co.elastic.gradle.vault.VaultAuthenticationExtension;
import co.elastic.gradle.vault.VaultExtension;
import co.elastic.gradle.vault.VaultPlugin;
import com.gradle.CommonCustomUserDataGradlePlugin;
import com.gradle.develocity.agent.gradle.DevelocityConfiguration;
import com.gradle.develocity.agent.gradle.DevelocityPlugin;
import com.gradle.develocity.agent.gradle.scan.BuildResult;
import com.gradle.develocity.agent.gradle.scan.BuildScanConfiguration;
import com.gradle.develocity.agent.gradle.scan.BuildScanDataObfuscationConfiguration;
import org.gradle.api.Action;
import org.gradle.api.GradleException;
import org.gradle.api.Plugin;
import org.gradle.api.Project;
import org.gradle.api.initialization.Settings;
import org.gradle.api.plugins.ExtensionsSchema;
import org.gradle.api.plugins.PluginAware;
import org.gradle.api.plugins.PluginContainer;
import org.gradle.internal.jvm.Jvm;

import java.io.*;
import java.net.MalformedURLException;
import java.net.URL;
import java.net.URLEncoder;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.concurrent.TimeUnit;

@SuppressWarnings("unused")
public class ElasticConventionsPlugin implements Plugin<PluginAware> {

    public static final String PROPERTY_NAME_VAULT_PREFIX = "co.elastic.vault_prefix";

    private String getVaultArtifactoryPath(Project target) {

        return getVaultPrefix(target) + "/artifactory_creds";
    }

    private  String getSnykVaultPath(Project target) {
        return getVaultPrefix(target) + "/snyk_api_key";
    }

    private String getVaultPrefix(Project target) {
        final Object vaultPrefixFromProperty = target.getProperties().get(PROPERTY_NAME_VAULT_PREFIX);
        if (vaultPrefixFromProperty == null) {
            throw new GradleException("This plugin requires the co.elastic.vault_prefix to be set for the vault integration. " +
                                      "Most of the time this needs to be set in the gradle.properties in your repo to `secret/ci/elastic-<name of your repo>`.");
        }
        return vaultPrefixFromProperty.toString();
    }

    @Override
    public void apply(PluginAware target) {
        if (target instanceof Project project) {
            applyToProject(project);
        } else if (target instanceof Settings settings) {
            applyToSettings(settings);
        }
    }

    public void applyToProject(Project target) {
        final PluginContainer plugins = target.getPlugins();
        plugins.apply(LifecyclePlugin.class);
        plugins.apply(VaultPlugin.class);
        final VaultExtension vault = target.getExtensions().getByType(VaultExtension.class);
        plugins.withType(VaultPlugin.class, unused ->
                configureVaultPlugin(vault)
        );
        target.getRootProject().getPlugins().withType(VaultPlugin.class, unused ->
                configureVaultPlugin(target.getRootProject().getExtensions().getByType(VaultExtension.class))
        );

        configureCliPlugins(target);

        target.afterEvaluate(unused -> {
                target.getTasks().withType(SnykCLIExecTask.class, task -> {
                    target.getLogger().info("Configuring Snyk token env var for " + task.getPath());
                    task.environment(
                            "SNYK_TOKEN",
                            vault.readAndCacheSecret(getSnykVaultPath(target)).get().get("apikey")
                    );
                });
        });

        target.getPlugins().withType(DockerBaseImageBuildPlugin.class, unused -> {
            target.getPlugins().apply(VaultPlugin.class);
            final BaseImageExtension extension = target.getExtensions().getByType(BaseImageExtension.class);
            var creds = vault.readAndCacheSecret(getVaultArtifactoryPath(target)).get();
            try {
                extension.getOsPackageRepository().set(new URL(
                        "https://" + creds.get("username") + ":" + creds.get("plaintext") +
                        "@artifactory.elastic.dev/artifactory/gradle-plugins-os-packages"
                ));
            } catch (MalformedURLException e) {
                throw new GradleException("Can't configure os package repository", e);
            }
        });
    }

    private void configureCliPlugins(Project target) {
        final VaultExtension vault = target.getExtensions().getByType(VaultExtension.class);
        target.afterEvaluate(unused ->
            target.getPlugins().withType(BaseCliPlugin.class, u -> {
                final CliExtension cliExtension = target.getExtensions().getByType(CliExtension.class);
                var listOfNames = new ArrayList<String>();
                for (ExtensionsSchema.ExtensionSchema extensionSchema : cliExtension.getExtensions().getExtensionsSchema()) {
                    if (extensionSchema.getPublicType().isAssignableFrom(BaseCLiExtension.class)) {
                        listOfNames.add(extensionSchema.getName());
                    }
                }
                var creds = vault.readAndCacheSecret(getVaultArtifactoryPath(target)).get();
                for (String name : listOfNames) {
                    final BaseCLiExtension extension = (BaseCLiExtension) cliExtension.getExtensions().getByName(name);
                    extension.getUsername().set(creds.get("username"));
                    extension.getPassword().set(creds.get("plaintext"));
                }
            })
        );
    }

    public void applyToSettings(Settings target) {
        target.getPlugins().withType(VaultPlugin.class, unused -> {
            configureVaultPlugin(target.getExtensions().getByType(VaultExtension.class));
        });

        configureGradleEnterprise(target);
    }

    private void configureGradleEnterprise(Settings target) {
        target.getPlugins().apply(DevelocityPlugin.class);
        target.getPlugins().apply(CommonCustomUserDataGradlePlugin.class);
        final DevelocityConfiguration develocity = target.getExtensions().getByType(DevelocityConfiguration.class);
        final BuildScanConfiguration buildScan = develocity.getBuildScan();
        final BuildScanDataObfuscationConfiguration obfuscation = buildScan.getObfuscation();
        final CustomValueSearchLinker customValueSearchLinker = CustomValueSearchLinker.registerWith(develocity, buildScan);

        boolean isCI = System.getenv("BUILD_URL") != null || System.getenv("BUILDKITE_BUILD_URL") != null;
        // Don't publish in the background on CI since we use ephemeral workers
        buildScan.getUploadInBackground().set(!isCI);
        develocity.getServer().set("https://gradle-enterprise.elastic.co");
        obfuscation.ipAddresses(ip -> ip.stream().map(it -> "0.0.0.0").toList());

        final Jvm jvm = Jvm.current();
        buildScan.value("Gradle Daemon Java Home", jvm.getJavaHome().getAbsolutePath());
        buildScan.value("Gradle Daemon Java Version", jvm.getJavaVersion().toString());

        var buildNumber = getFirsEnvVar("BUILD_NUMBER", "BUILDKITE_BUILD_NUMBER");
        var buildUrl = getFirsEnvVar("BUILD_URL", "BUILDKITE_BUILD_URL");
        var jobName = getFirsEnvVar("JOB_NAME", "BUILDKITE_PIPELINE_NAME");
        var nodeName = getFirsEnvVar("NODE_NAME", "BUILDKITE_AGENT_NAME");

        if (isCI) {
            buildScan.tag("CI");
        }

        buildUrl.ifPresent(s -> buildScan.link("CI URL", s));
        jobName.ifPresent(s -> customValueSearchLinker.addCustomValueAndSearchLink("Pipeline name", s));
        getFirsEnvVar("CHANGE_TARGET").ifPresent(s -> buildScan.link("PR", s));

        buildScan.background(it -> {
            customValueSearchLinker.addCustomValueAndSearchLink(
                    "Git Parent",
                    execAndGetStdOut("git", "rev-parse", "--verify", "HEAD^1")
            );
        });

        getFirsEnvVar("NODE_LABELS", "BUILDKITE_AGENT_META_DATA_QUEUE").map(it -> it.split(" ")).ifPresent(labels ->
                Arrays.stream(labels).forEach(label -> customValueSearchLinker.addCustomValueAndSearchLink("CI Worker Label", label))
        );
    }

    // copied from https://github.com/gradle/common-custom-user-data-gradle-plugin/blob/main/src/main/java/com/gradle/Utils.java
    static String execAndGetStdOut(String... args) {
        Runtime runtime = Runtime.getRuntime();
        Process process;
        try {
            process = runtime.exec(args);
        } catch (IOException e) {
            throw new RuntimeException(e);
        }

        try (Reader standard = new BufferedReader(new InputStreamReader(process.getInputStream(), Charset.defaultCharset()))) {
            try (Reader error = new BufferedReader(new InputStreamReader(process.getErrorStream(), Charset.defaultCharset()))) {
                String standardText = readFully(standard);
                String ignore = readFully(error);

                boolean finished = process.waitFor(10, TimeUnit.SECONDS);
                return finished && process.exitValue() == 0 ? trimAtEnd(standardText) : null;
            }
        } catch (IOException | InterruptedException e) {
            throw new RuntimeException(e);
        } finally {
            process.destroyForcibly();
        }
    }

    private static String readFully(Reader reader) throws IOException {
        StringBuilder sb = new StringBuilder();
        char[] buf = new char[1024];
        int nRead;
        while ((nRead = reader.read(buf)) != -1) {
            sb.append(buf, 0, nRead);
        }
        return sb.toString();
    }

    private static String trimAtEnd(String str) {
        return ('x' + str).trim().substring(1);
    }

    public Optional<String> getFirsEnvVar(String... names) {
        for (String name : names) {
            final String value = System.getenv(name);
            if (value != null) {
                return Optional.of(value);
            }
        }
        return Optional.empty();
    }

    public void configureVaultPlugin(VaultExtension extension) {
        extension.getAddress().set("https://vault-ci-prod.elastic.dev");
        final VaultAuthenticationExtension auth = extension.getExtensions()
                .getByType(VaultAuthenticationExtension.class);
        // NOTE that this is in order of precedence
        auth.tokenEnv();
        auth.roleAndSecretEnv();
        auth.ghTokenEnv();
        auth.ghTokenFile();
    }

    /**
     * copied from https://github.com/gradle/common-custom-user-data-gradle-plugin/blob/main/src/main/java/com/gradle/CustomBuildScanEnhancements.java
     * Collects custom values that should have a search link, and creates these links in `buildFinished`.
     * The actual construction of the links must be deferred to ensure the Server URL is set.
     * This functionality needs to be in a separate static class in order to work with configuration cache.
     */
    private static final class CustomValueSearchLinker implements Action<BuildResult> {

        private final BuildScanConfiguration buildScan;
        private final DevelocityConfiguration develocity;
        private final Map<String, String> customValueLinks;

        private CustomValueSearchLinker(DevelocityConfiguration develocity, BuildScanConfiguration buildScan) {
            this.develocity = develocity;
            this.buildScan = buildScan;
            this.customValueLinks = new LinkedHashMap<>();
        }

        private static CustomValueSearchLinker registerWith(DevelocityConfiguration develocity, BuildScanConfiguration buildScan) {
            CustomValueSearchLinker customValueSearchLinker = new CustomValueSearchLinker(develocity, buildScan);
            buildScan.buildFinished(customValueSearchLinker);
            return customValueSearchLinker;
        }

        private void addCustomValueAndSearchLink(String name, String value) {
            buildScan.value(name, value);
            registerLink(name, name, value);
        }

        public void addCustomValueAndSearchLink(String linkLabel, String name, String value) {
            buildScan.value(name, value);
            registerLink(linkLabel, name, value);
        }

        private synchronized void registerLink(String linkLabel, String name, String value) {
            String searchParams = "search.names=" + urlEncode(name) + "&search.values=" + urlEncode(value);
            customValueLinks.put(linkLabel, searchParams);
        }

        @Override
        public synchronized void execute(BuildResult buildResult) {
            String server = develocity.getServer().get();
            if (server != null) {
                customValueLinks.forEach((linkLabel, searchParams) -> {
                    String url = appendIfMissing(server, "/") + "scans?" + searchParams + "#selection.buildScanB=" + urlEncode("{SCAN_ID}");
                    buildScan.link(linkLabel + " build scans", url);
                });
            }
        }

        static String appendIfMissing(String str, String suffix) {
            return str.endsWith(suffix) ? str : str + suffix;
        }

        static String urlEncode(String str) {
            try {
                return URLEncoder.encode(str, StandardCharsets.UTF_8.name());
            } catch (UnsupportedEncodingException e) {
                throw new RuntimeException(e);
            }
        }

    }
}
