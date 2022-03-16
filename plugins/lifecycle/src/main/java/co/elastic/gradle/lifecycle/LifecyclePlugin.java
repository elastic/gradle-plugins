package co.elastic.gradle.lifecycle;

import co.elastic.gradle.utils.GradleUtils;
import org.gradle.api.Plugin;
import org.gradle.api.Project;
import org.gradle.api.Task;
import org.gradle.api.plugins.BasePlugin;
import org.gradle.api.tasks.TaskContainer;
import org.gradle.api.tasks.TaskProvider;

public class LifecyclePlugin implements Plugin<Project> {

    public static final String RESOLVE_ALL_DEPENDENCIES_TASK_NAME = "resolveAllDependencies";
    public static final String PUBLISH_TASK_NAME = "publish";
    public static final String SYNC_BIN_DIR_TASK_NAME = "syncBinDir";
    public static final String PRE_COMMIT_TASK_NAME = "preCommit";

    @Override
    public void apply(Project target) {
        final TaskContainer tasks = target.getTasks();

        target.getPluginManager().apply(BasePlugin.class);

        // Some plug-ins like maven publishing might already define a "publish" task and will conflict with this one.
        // We use an existing one if available. The order in which the plugins maters.
        final TaskProvider<Task> publish = GradleUtils.registerOrGet(target, PUBLISH_TASK_NAME);
        publish.configure(task -> {
            task.setGroup("publishing");
            task.setDescription("Lifecycle task to publish build artefacts to external repos (e.g. Docker images)");
            task.dependsOn(tasks.named("build"));
        });

        tasks.register(RESOLVE_ALL_DEPENDENCIES_TASK_NAME, ResolveAllDependenciesTask.class);

        final TaskProvider<Task> syncBinDir = tasks.register(SYNC_BIN_DIR_TASK_NAME, task -> {
            task.setGroup("utilities");
            task.setDescription("Lifecycle task to create links to \"bin dir\" that can be added to path so that tools " +
                                "used by Gradle can be used on the cli."
            );
        });

        tasks.named("check", task -> task.dependsOn(syncBinDir));

        tasks.register(PRE_COMMIT_TASK_NAME, task -> {
            task.setGroup("verification");
            task.setDescription("Implements a set of \"quick\" checks, e.g. linting and compilation that one can use to keep the repository clean.");
        });
    }


}
