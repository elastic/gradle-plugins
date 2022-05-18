package co.elastic.gradle.snyk;

import co.elastic.gradle.cli.base.BaseCLiExtension;
import co.elastic.gradle.cli.base.BaseCliPlugin;
import co.elastic.gradle.cli.base.CliExtension;
import co.elastic.gradle.utils.Architecture;
import co.elastic.gradle.utils.OS;
import co.elastic.gradle.utils.PrefixingOutputStream;
import org.gradle.api.GradleException;
import org.gradle.api.Plugin;
import org.gradle.api.Project;

import java.net.MalformedURLException;
import java.net.URL;
import java.util.Collections;
import java.util.List;

public class SnykPlugin implements Plugin<Project> {

    @Override
    public void apply(Project target) {
        target.getPluginManager().apply(BaseCliPlugin.class);
        final BaseCLiExtension extension = target.getExtensions().getByType(CliExtension.class)
                .getExtensions()
                .create("snyk", BaseCLiExtension.class);
        extension.getVersion().convention("v1.856.0");
        try {
            extension.getBaseURL()
                    .convention(new URL("https://artifactory.elastic.dev/artifactory/snyk-release-proxy"));
        } catch (MalformedURLException e) {
            throw new IllegalStateException(e);
        }
        extension.getPattern().convention("cli/[revision]/[module]-[classifier]");

        target.afterEvaluate(p -> {
            BaseCliPlugin.addDownloadRepo(target, extension);
            for (String kind : List.of("linux", "macos", "linux-arm64")) {
                BaseCliPlugin.addDependency(
                        target,
                        "snyk:snyk:" + extension.getVersion().get() + ":" + kind

                );
            }
        });

        target.getTasks().withType(SnykCLIExecTask.class).configureEach(task -> {
            task.dependsOn(":" + BaseCliPlugin.SYNC_TASK_NAME);
            task.setExecutable(BaseCliPlugin.getExecutable(target, "snyk"));
            task.setEnvironment(Collections.emptyMap());
            task.environment("SNYK_CFG_DISABLESUGGESTIONS", "true");
            task.environment("FORCE_COLOR", "true");
            task.setIgnoreExitValue(true);
            task.setStandardOutput(new PrefixingOutputStream("[snyk] ", System.out));
            task.setErrorOutput(new PrefixingOutputStream("[snyl] ", System.err));
            task.doLast(it -> {
                if (task.getExecutionResult().get().getExitValue() != 0) {
                    throw new GradleException("Snyk scan failed, check the task output for details");
                }
            });
        });
    }

    public static String getKind(final OS os, final Architecture arch) {
        switch (os) {
            case DARWIN:
                // No arm mac binaries as of this writing
                return "macos";
            case LINUX: {
                switch (arch) {
                    case AARCH64:
                        return "linux-arm64";
                    case X86_64:
                        return "linux";
                }
            }
            default:
                throw new GradleException("Unsupported OS: " + os);
        }
    }

}
