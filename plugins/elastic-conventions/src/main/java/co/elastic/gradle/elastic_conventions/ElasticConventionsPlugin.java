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
import com.gradle.enterprise.gradleplugin.GradleEnterpriseExtension;
import com.gradle.enterprise.gradleplugin.GradleEnterprisePlugin;
import com.gradle.scan.plugin.BuildResult;
import com.gradle.scan.plugin.BuildScanDataObfuscation;
import com.gradle.scan.plugin.BuildScanExtension;
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
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.concurrent.TimeUnit;

@SuppressWarnings("unused")
public class ElasticConventionsPlugin implements Plugin<PluginAware> {
    public static final int OBSERVABILITY_TIME_FRAME_MINUTES = 5;
    public static final String VAULT_ARTIFACTORY_PATH = "secret/cloud-team/cloud-ci/artifactory_creds";

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
        plugins.withType(VaultPlugin.class, unused ->
                configureVaultPlugin(target.getExtensions().getByType(VaultExtension.class))
        );

        configureCliPlugins(target, plugins);



        target.getPlugins().withType(DockerBaseImageBuildPlugin.class, unused -> {
            plugins.apply(VaultPlugin.class);
            final BaseImageExtension extension = target.getExtensions().getByType(BaseImageExtension.class);
            final VaultExtension vault = target.getExtensions().getByType(VaultExtension.class);

            target.getTasks().withType(SnykCLIExecTask.class, task ->
                    task.environment(
                            "SNYK_TOKEN",
                            vault.readAndCacheSecret("secret/cloud-team/cloud-ci/snyk_api_key").get().get("plaintext")
                    )
            );

            var creds = vault.readAndCacheSecret(VAULT_ARTIFACTORY_PATH).get();
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

    private void configureCliPlugins(Project target, PluginContainer plugins) {
        plugins.withType(BaseCliPlugin.class, unused -> {
            plugins.apply(VaultPlugin.class);
            final VaultExtension vault = target.getExtensions().getByType(VaultExtension.class);
            final CliExtension cliExtension = target.getExtensions().getByType(CliExtension.class);
            var listOfNames = new ArrayList<String>();
            for (ExtensionsSchema.ExtensionSchema extensionSchema : cliExtension.getExtensions().getExtensionsSchema()) {
                if (extensionSchema.getPublicType().isAssignableFrom(BaseCLiExtension.class)) {
                    listOfNames.add(extensionSchema.getName());
                }
            }
            var creds = vault.readAndCacheSecret(VAULT_ARTIFACTORY_PATH).get();
            for (String name : listOfNames) {
                final BaseCLiExtension extension = (BaseCLiExtension) cliExtension.getExtensions().getByName(name);
                extension.getUsername().set(creds.get("username"));
                extension.getPassword().set(creds.get("plaintext"));
            }
        });
    }

    public void applyToSettings(Settings target) {
        target.getPlugins().withType(VaultPlugin.class, unused -> {
            configureVaultPlugin(target.getExtensions().getByType(VaultExtension.class));
        });

        configureGradleEnterprise(target);
    }

    private void configureGradleEnterprise(Settings target) {
        target.getPlugins().apply(GradleEnterprisePlugin.class);
        target.getPlugins().apply(CommonCustomUserDataGradlePlugin.class);
        final GradleEnterpriseExtension gradleEnterprise = target.getExtensions().getByType(GradleEnterpriseExtension.class);
        final BuildScanExtension buildScan = gradleEnterprise.getBuildScan();
        final BuildScanDataObfuscation obfuscation = buildScan.getObfuscation();
        final CustomValueSearchLinker customValueSearchLinker = CustomValueSearchLinker.registerWith(buildScan);

        buildScan.publishAlways();

        boolean isCI = System.getenv("BUILD_URL") != null || System.getenv("BUILDKITE_BUILD_URL") != null;
        // Don't publish in the background on CI since we use ephemeral workers
        buildScan.setUploadInBackground(!isCI);
        buildScan.setServer("https://gradle-enterprise.elastic.co");
        obfuscation.ipAddresses(ip -> ip.stream().map(it -> "0.0.0.0").toList());

        final Jvm jvm = Jvm.current();
        buildScan.value("Gradle Daemon Java Home", jvm.getJavaHome().getAbsolutePath());
        buildScan.value("Gradle Daemon Java Version", jvm.getJavaVersion().toString());

        var buildNumber = getFirsEnvVar("BUILD_NUMBER", "BUILDKITE_BUILD_NUMBER");
        var buildUrl = getFirsEnvVar("BUILD_URL", "BUILDKITE_BUILD_URL");
        var jobName = getFirsEnvVar("JOB_NAME", "BUILDKITE_PIPELINE_NAME");
        var nodeName = getFirsEnvVar("NODE_NAME", "BUILDKITE_AGENT_NAME");

        var startTime = OffsetDateTime.now(ZoneOffset.UTC);
        nodeName.ifPresent(s -> buildScan.buildFinished(result -> linkToObservability(buildScan, startTime, s)));

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

    private void linkToObservability(BuildScanExtension buildScan, OffsetDateTime startTime, String nodeName) {
        var from = startTime.minusMinutes(OBSERVABILITY_TIME_FRAME_MINUTES)
                .format(DateTimeFormatter.ISO_ZONED_DATE_TIME);
        var to = OffsetDateTime.now(ZoneOffset.UTC)
                .plusMinutes(OBSERVABILITY_TIME_FRAME_MINUTES)
                .format(DateTimeFormatter.ISO_ZONED_DATE_TIME);
        buildScan.link(
                "APM: CPU, Load, Memory, FS Metrics",
                "https://ci-stats.elastic.co/app/metrics/explorer?" +
                "metricsExplorer=(" +
                "chartOptions:(stack:!f,type:line,yAxisMode:fromZero)," +
                "options:(" +
                "aggregation:avg," +
                "filterQuery:%27host.name:" + nodeName + "%27," +
                "metrics:!(" +
                "(aggregation:avg,color:color0,field:system.cpu.total.norm.pct)," +
                "(aggregation:avg,color:color1,field:system.load.norm.1)," +
                "(aggregation:avg,color:color2,field:system.load.norm.5)," +
                "(aggregation:avg,color:color3,field:system.load.norm.15)," +
                "(aggregation:avg,color:color4,field:system.memory.actual.used.pct)," +
                "(aggregation:avg,color:color5,field:system.core.steal.pct)," +
                "(aggregation:avg,color:color6,field:system.filesystem.used.pct)" +
                ")," +
                "source:url" +
                ")," +
                "timerange:(from:%27" + to + "%27,interval:%3E%3D10s,to:%27" + from + "%27)" +
                ")"
        );

        buildScan.link(
                "APM: IO Latency Metrics",
                "https://ci-stats.elastic.co/app/metrics/explorer?" +
                "metricsExplorer=(" +
                "chartOptions:(stack:!f,type:line,yAxisMode:fromZero)," +
                "options:(" +
                "aggregation:avg," +
                "filterQuery:%27host.name:" + nodeName + "%27," +
                "metrics:!(" +
                "(aggregation:avg,color:color0,field:system.diskio.iostat.write.await)," +
                "(aggregation:avg,color:color1,field:system.diskio.iostat.read.await)," +
                "(aggregation:avg,color:color2,field:system.diskio.iostat.service_time)" +
                ")," +
                "source:url" +
                ")," +
                "timerange:(from:%27" + from +  "%27,interval:%3E%3D10s,to:%27" + to + "%27)" +
                ")"
        );
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
        extension.getAddress().set("https://secrets.elastic.co:8200");
        final VaultAuthenticationExtension auth = extension.getExtensions()
                .getByType(VaultAuthenticationExtension.class);
        auth.ghTokenFile();
        auth.ghTokenEnv();
        auth.tokenEnv();
        auth.roleAndSecretEnv();
    }

    /**
     * copied from https://github.com/gradle/common-custom-user-data-gradle-plugin/blob/main/src/main/java/com/gradle/CustomBuildScanEnhancements.java
     * Collects custom values that should have a search link, and creates these links in `buildFinished`.
     * The actual construction of the links must be deferred to ensure the Server URL is set.
     * This functionality needs to be in a separate static class in order to work with configuration cache.
     */
    private static final class CustomValueSearchLinker implements Action<BuildResult> {

        private final BuildScanExtension buildScan;
        private final Map<String, String> customValueLinks;

        private CustomValueSearchLinker(BuildScanExtension buildScan) {
            this.buildScan = buildScan;
            this.customValueLinks = new LinkedHashMap<>();
        }

        private static CustomValueSearchLinker registerWith(BuildScanExtension buildScan) {
            CustomValueSearchLinker customValueSearchLinker = new CustomValueSearchLinker(buildScan);
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
            String server = buildScan.getServer();
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
