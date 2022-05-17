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
import java.util.Arrays;

import java.util.Collections;

public class ManifestToolPlugin implements Plugin<Project> {

    public static File getExecutable(Project target) {
        return BaseCliPlugin.getExecutable(target, "manifest-tool");
    }

    @Override
    public void apply(Project target) {
        target.getPluginManager().apply(BaseCliPlugin.class);

        final BaseCLiExtension extension = target.getExtensions().getByType(CliExtension.class)
                .getExtensions()
                .create("manifestTool", BaseCLiExtension.class, "manifest-tool");
        extension.getVersion().convention("v1.0.3");

        target.afterEvaluate(p -> {
            BaseCliPlugin.addDownloadRepo(target, extension);
            Arrays.stream(OS.values()).forEach(os ->
                    Arrays.stream(Architecture.values())
                            .filter(arch -> !(OS.current().equals(OS.DARWIN) && arch.equals(Architecture.AARCH64)))
                            .forEach(arch -> {
                                        BaseCliPlugin.addDependency(
                                                target,
                                                "estesp/manifest-tool:manifest-tool:" +
                                                extension.getVersion().get() + ":" +
                                                os.name().toLowerCase() + "-" +
                                                arch.dockerName()
                                        );
                                    }
                            )
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


}
