package co.elastic.gradle.cli.manifest;

import co.elastic.gradle.cli.base.BaseCLiExtension;
import co.elastic.gradle.cli.base.BaseCliPlugin;
import co.elastic.gradle.cli.base.CliExtension;
import co.elastic.gradle.utils.Architecture;
import co.elastic.gradle.utils.OS;
import co.elastic.gradle.utils.PrefixingOutputStream;
import org.gradle.api.Plugin;
import org.gradle.api.Project;

import java.io.File;
import java.util.Collections;
import java.util.Map;

public class ManifestToolPlugin implements Plugin<Project> {

    public static File getExecutable(Project target) {
        return BaseCliPlugin.getExecutable(target, "manifest-tool");
    }

    @Override
    public void apply(Project target) {
        target.getPluginManager().apply(BaseCliPlugin.class);

        final BaseCLiExtension extension = target.getExtensions().getByType(CliExtension.class)
                .getExtensions()
                .create("manifestTool", BaseCLiExtension.class);
        extension.getVersion().convention("v1.0.3");

        target.afterEvaluate(p -> {
            BaseCliPlugin.addDownloadRepo(target, extension);
            BaseCliPlugin.addDependency(target,
                    "estesp/manifest-tool:manifest-tool:" + extension.getVersion().get() + ":" + getKind()
            );
        });

        target.getTasks().withType(ManifestToolExecTask.class)
                .configureEach(task -> {
                    task.setEnvironment(Collections.emptyMap());
                    task.setExecutable(getExecutable(target));
                    task.dependsOn(":" + BaseCliPlugin.SYNC_TASK_NAME);
                    task.setStandardOutput(new PrefixingOutputStream("[manifest-tool] ", System.out));
                    task.setErrorOutput(new PrefixingOutputStream("[manifest-tool] ", System.err));
                });
    }

    private String getKind() {
        return OS.current().toString().toLowerCase() + "-" +
               Architecture.current()
                       .map(Map.of(
                               // Use emulation, no native release for darwin yet
                               Architecture.AARCH64, OS.current().equals(OS.DARWIN) ? "amd64" : "arm64",
                               Architecture.X86_64, "amd64"
                       ));
    }
}
