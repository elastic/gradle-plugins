package co.elastic.gradle.cli.shellcheck;

import co.elastic.gradle.cli.base.BaseCLiExtension;
import co.elastic.gradle.cli.base.BaseCliPlugin;
import co.elastic.gradle.cli.base.CliExtension;
import co.elastic.gradle.lifecycle.LifecyclePlugin;
import co.elastic.gradle.lifecycle.MultiArchLifecyclePlugin;
import co.elastic.gradle.utils.Architecture;
import co.elastic.gradle.utils.OS;
import org.gradle.api.Plugin;
import org.gradle.api.Project;
import org.gradle.api.file.FileCollection;
import org.gradle.api.tasks.TaskProvider;

import java.nio.file.Files;
import java.util.Arrays;
import java.util.Locale;

@SuppressWarnings("unused")
public class ShellcheckPlugin implements Plugin<Project> {

    @Override
    public void apply(Project target) {
        target.getPluginManager().apply(BaseCliPlugin.class);
        final BaseCLiExtension extension = target.getExtensions().getByType(CliExtension.class)
                .getExtensions()
                .create("shellcheck", BaseCLiExtension.class, "shellcheck");
        extension.getVersion().convention("v0.8.0");

        extension.getPattern()
                .convention("[organisation]/releases/download/[revision]/[module]-[revision].[classifier]");

        target.afterEvaluate(p -> {
            BaseCliPlugin.addDownloadRepo(target, extension);
            Arrays.stream(OS.values()).forEach(os ->
                    Arrays.stream(Architecture.values())
                            .filter(arch -> !(OS.current().equals(OS.DARWIN) && arch.equals(Architecture.AARCH64)))
                            .forEach(arch -> {
                                        BaseCliPlugin.addDependency(
                                                target,
                                                "koalaman/shellcheck:shellcheck:" +
                                                extension.getVersion().get() + ":" +
                                                os.name().toLowerCase() + "." +
                                                arch.name().toLowerCase(Locale.ROOT) + ".tar.xz"
                                        );
                                    }
                            )
            );
        });

        final TaskProvider<ShellcheckTask> shellcheck = target.getTasks().register(
                "shellcheck",
                ShellcheckTask.class
        );
        if (Files.exists(target.getProjectDir().toPath().resolve("src"))) {
            shellcheck.configure(task -> task.check((FileCollection) target.fileTree("src").include("**/*.sh")));
        }

        target.getTasks().withType(ShellcheckTask.class).configureEach( task -> {
            task.getTool().set(BaseCliPlugin.getExecutable(target, "shellcheck"));
            task.dependsOn(":" + BaseCliPlugin.SYNC_TASK_NAME);
        });

        MultiArchLifecyclePlugin.checkPlatformIndependent(target, shellcheck);
        LifecyclePlugin.check(target, shellcheck);
    }

}
