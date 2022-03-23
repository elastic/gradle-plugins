package co.elastic.gradle.lifecycle;

import co.elastic.gradle.utils.GradleUtils;
import org.gradle.api.Plugin;
import org.gradle.api.Project;
import org.gradle.api.Task;
import org.gradle.api.plugins.BasePlugin;
import org.gradle.api.tasks.TaskContainer;
import org.gradle.api.tasks.TaskProvider;
import org.gradle.language.base.plugins.LifecycleBasePlugin;

public class LifecyclePlugin implements Plugin<Project> {

    protected static final String RESOLVE_ALL_DEPENDENCIES_TASK_NAME = "resolveAllDependencies";
    protected static final String PUBLISH_TASK_NAME = "publish";
    protected static final String SYNC_BIN_DIR_TASK_NAME = "syncBinDir";
    protected static final String PRE_COMMIT_TASK_NAME = "preCommit";
    protected static final String AUTO_FIX_TASK_NAME = "autoFix";


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

        tasks.named(LifecycleBasePlugin.CHECK_TASK_NAME, task -> task.dependsOn(syncBinDir));

        tasks.register(PRE_COMMIT_TASK_NAME, task -> {
            task.setGroup("verification");
            task.setDescription("Implements a set of \"quick\" checks, e.g. linting and compilation that one can use to keep the repository clean.");
        });

        tasks.register(AUTO_FIX_TASK_NAME, task -> {
           task.setGroup("automation");
           task.setDescription("Automatically fix some problems, e.g. run linters with the --fix option");
        });
    }

    private static void whenPluginAddedAddDependency(Project target, TaskProvider<? extends Task> dependency, String resolveAllDependenciesTaskName) {
        target.getPluginManager().withPlugin("co.elastic.lifecycle", p -> {
            target.getTasks().named(resolveAllDependenciesTaskName, task -> {
                task.dependsOn(dependency);
            });
        });
    }

    public static void resolveAllDependencies(Project target, TaskProvider<? extends Task> dependency) {
        whenPluginAddedAddDependency(target, dependency, RESOLVE_ALL_DEPENDENCIES_TASK_NAME);
    }

    public static void publish(Project target, TaskProvider<? extends Task> dependency) {
        whenPluginAddedAddDependency(target, dependency, PUBLISH_TASK_NAME);
    }

    public static void syncBinDir(Project target, TaskProvider<? extends Task> dependency) {
        whenPluginAddedAddDependency(target, dependency, SYNC_BIN_DIR_TASK_NAME);
    }

    public static void preCommit(Project target, TaskProvider<? extends Task> dependency) {
        whenPluginAddedAddDependency(target, dependency, PRE_COMMIT_TASK_NAME);
    }

    public static void check(Project target, TaskProvider<? extends Task> dependency) {
        whenPluginAddedAddDependency(target, dependency, LifecycleBasePlugin.CHECK_TASK_NAME);
    }

    public static void assemble(Project target, TaskProvider<? extends Task> dependency) {
        whenPluginAddedAddDependency(target, dependency, LifecycleBasePlugin.ASSEMBLE_TASK_NAME);
    }

    public static void clean(Project target, TaskProvider<? extends Task> dependency) {
        whenPluginAddedAddDependency(target, dependency, LifecycleBasePlugin.CLEAN_TASK_NAME);
    }

    public static void autoFix(Project target, TaskProvider<? extends Task> dependency) {
        whenPluginAddedAddDependency(target, dependency, AUTO_FIX_TASK_NAME);
    }
}
