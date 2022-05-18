package co.elastic.gradle.cli.jfrog;

import co.elastic.gradle.cli.base.BaseCLiExtension;
import co.elastic.gradle.cli.base.BaseCliPlugin;
import co.elastic.gradle.cli.base.CliExtension;
import co.elastic.gradle.utils.Architecture;
import co.elastic.gradle.utils.OS;
import org.gradle.api.GradleException;
import org.gradle.api.Plugin;
import org.gradle.api.Project;

import java.io.File;
import java.net.MalformedURLException;
import java.net.URL;
import java.util.Arrays;

@SuppressWarnings("unused")
public class JFrogPlugin implements Plugin<Project> {

    @Override
    public void apply(Project target) {
        target.getPluginManager().apply(BaseCliPlugin.class);
        final BaseCLiExtension extension = target.getExtensions().getByType(CliExtension.class)
                .getExtensions()
                .create("jfrog", BaseCLiExtension.class);
        extension.getVersion().convention("2.16.4");
        extension.getPattern().convention("[organisation]/[module]/v2-jf/[revision]/[module]-[classifier]/jf");
        try {
            extension.getBaseURL().convention(new URL("https://artifactory.elastic.dev/artifactory/jfrog-release-proxy"));
        } catch (MalformedURLException e) {
            throw new IllegalStateException(e);
        }

        target.afterEvaluate(p -> {
            BaseCliPlugin.addDownloadRepo(target, extension);
            Arrays.stream(OS.values()).forEach(os ->
                    Arrays.stream(Architecture.values())
                           .forEach(arch -> {
                                BaseCliPlugin.addDependency(
                                        target,
                                        "artifactory:jfrog-cli:" +
                                        extension.getVersion().get() + ":" +
                                        getKind(os, arch)
                                );
                            })
            );
        });

        target.getTasks().withType(JFrogCliUsingTask.class, t -> {
            t.getJFrogCli().set(BaseCliPlugin.getExecutable(target, "jfrog-cli"));
            t.dependsOn(":" + BaseCliPlugin.SYNC_TASK_NAME);
        });
        target.getTasks().withType(JFrogCliExecTask.class, t -> {
            t.setExecutable(BaseCliPlugin.getExecutable(target, "jfrog-cli"));
            t.dependsOn(":" + BaseCliPlugin.SYNC_TASK_NAME);
        });
    }

    public static File getExecutable(Project target) {
        return BaseCliPlugin.getExecutable(target, "jfrog-cli");
    }

    public static File getExecutable(Project target, OS os) {
        return BaseCliPlugin.getExecutable(target, "jfrog-cli", os, Architecture.current());
    }

    private static String getKind(final OS os, final Architecture arch) {
        switch (os) {
            case DARWIN:
                // No arm mac binaries as of this writing
                return "mac-386";
            case LINUX: {
                return switch (arch) {
                    case AARCH64 -> "linux-arm64";
                    case X86_64 -> "linux-amd64";
                };
            }
            default:
                throw new GradleException("Unsupported OS: " + os);
        }
    }
}
