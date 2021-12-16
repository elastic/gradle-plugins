package co.elastic.cloud.gradle;

import co.elastic.cloud.gradle.util.RetryUtils;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.Sets;
import org.gradle.api.*;
import org.gradle.api.artifacts.Configuration;
import org.gradle.api.plugins.ApplicationPlugin;
import org.gradle.api.plugins.JavaPlugin;
import org.gradle.api.publish.maven.plugins.MavenPublishPlugin;
import org.gradle.api.tasks.TaskContainer;
import org.gradle.api.tasks.TaskDependency;
import org.gradle.api.tasks.TaskProvider;
import org.jetbrains.annotations.NotNull;

import java.util.Collections;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;

public class CloudBuildLifecyclePlugin implements Plugin<Project> {

    public static final String RESOLVE_ALL_DEPENDENCIES_TASK_NAME = "resolveAllDependencies";

    @Override
    public void apply(Project target) {
        final TaskContainer tasks = target.getTasks();

        target.apply(Collections.singletonMap("plugin", "base"));

        // Apply the maven-publish plugin for the "publish" task
        target.apply(t -> t.plugin(MavenPublishPlugin.class));

        tasks.register(RESOLVE_ALL_DEPENDENCIES_TASK_NAME, task -> {
            task.setDescription("Lifecycle task to resolves all external dependencies. " +
                    "This task can be used to cache everything locally so these are not  downloaded while building." +
                    " e.g. to download everything while on a better connection. In CI this is used to \"bake\" depdencies " +
                    "so they are not re-downloaded on every run."
            );
            task.setGroup("prepare");
            task.doLast(unused -> {
                task.getProject().getConfigurations().stream()
                        .filter(Configuration::isCanBeResolved)
                        .forEach(Configuration::resolve);
            });
        });

        tasks.named("publish", task -> {
            task.setGroup("publishing");
            task.setDescription("Lifecycle task to publish build artefacts to external repos (e.g. Docker images)");
            task.dependsOn(tasks.named("build"));
        });

        tasks.register("syncBinDir", task -> {
            task.setGroup("utilities");
            task.setDescription("Lifecycle task to create links to \"bin dir\" that can be added to path so that tools " +
                    "used by Gradle can be used on the cli."
            );
        });

        createMultiPlatformBuildLifecycle(tasks);

        tasks.register("preCommit", task -> {
            task.setGroup("verification");
            task.setDescription("Implements a set of \"quick\" checks, e.g. linting and compilation that one can use to keep the repository clean.");
        });
        tasks.named("checkPlatformIndependent", check -> check.dependsOn("preCommit"));

        // Deal with common lifecycle tasks from other plugins
        target.getPlugins().withType(JavaPlugin.class, plugin -> {
            tasks.named("checkPlatformIndependent", it -> it.dependsOn("test"));
            tasks.named("assemblePlatformIndependent", it -> it.dependsOn("jar"));
        });
        target.getPlugins().withType(ApplicationPlugin.class, plugin -> {
            tasks.named("checkPlatformIndependent", it -> it.dependsOn("test"));
            tasks.named("assemblePlatformIndependent", it -> it.dependsOn("distTar", "distZip"));
        });
    }

    private void createMultiPlatformBuildLifecycle(TaskContainer tasks) {
        final ImmutableMap<String, ImmutableMap<String, String>> taskMap = ImmutableMap.of(
                "check", ImmutableMap.of(
                        "ForPlatform", "Run all platform specific verifications for the current platform only.",
                        "PlatformIndependent", "Run all platform independent verifications " +
                                "(that don't depend on platform dependent tasks)",
                        "CombinePlatform", "Run all verifications for all platforms that can be ran in a platform independent way. " +
                                "Assumes that platform specific artefacts for this version are already published.",
                        "", "Run verification for the current platform and that are platform independent." +
                                " Useful for local testing."
                ),
                "assemble", ImmutableMap.of(
                        "ForPlatform", "Intended to create build artefacts, without running any tests for the current platform",
                        "PlatformIndependent", "Create platform independent build artefacts",
                        "CombinePlatform", "Create multi platform build artefacts, without running any tests.  " +
                                "This includes the production artifacts and generating documentation for all supported platforms." +
                                "Assumes that the platform specific artefacts were already published for this version.",
                        "", "Create platform specific artefacts for the current platform and emulate multi platform artefacts based on them." +
                                " Useful for local testing."
                ),
                "build", ImmutableMap.of(
                        "ForPlatform", "Create artefacts and run all platform specific verifications for the current platform only.",
                        "PlatformIndependent", "Create artefacts and run all platform independent verifications " +
                                "(that don't depend on platform dependent tasks)",
                        "CombinePlatform", "Create artefacts and  run all verifications for all platforms that can be ran in a platform independent way. " +
                                "Assumes that platform specific artefacts for this version are already published.",
                        "", "Create artefacts and  run verification for the current platform and that are platform independent." +
                                " Useful for local testing."
                ),
                "publish", ImmutableMap.of(
                        "ForPlatform", "Create, verify and publish all platform specific artefacts for the current platform only.",
                        "PlatformIndependent", "Create, verify and publish platform independent artefacts " +
                                "(that don't depend on platform dependent tasks)",
                        "CombinePlatform", "Create, verify and publish artefacts multi platform artefacts. " +
                                "Assumes that platform specific artefacts for this version are already published.",
                        "", "Create, verify  and run verification for the current platform and that are platform independent." +
                                " Useful for local testing."
                )
        );

        final ImmutableMap<String, String> groupLookup = ImmutableMap.of(
                "check", "verification",
                "assemble", "build"
        );

        taskMap.forEach((kind, descriptions) -> {
            descriptions.forEach((name, description) -> {
                final Action<Task> taskConfig = task -> {
                    if (kind != "publish") {
                        task.setGroup(groupLookup.getOrDefault(kind, kind));
                    } else {
                        task.setGroup("publishing");
                    }
                    task.setDescription(description);
                };
                if (name.isEmpty()) {
                    tasks.named(kind, taskConfig);
                } else {
                    tasks.register(kind + name, taskConfig);
                }
            });

            tasks.named(kind, task -> task.dependsOn(
                        tasks.named(kind + "ForPlatform"),
                        tasks.named(kind + "PlatformIndependent"),
                        tasks.named(kind + "CombinePlatform")
                ));
        });

        // The specialized "build" tasks have the specialized "check" and "assemble" tasks as dependencies, just like the base
        tasks.named("buildForPlatform", task -> {
            task.dependsOn("assembleForPlatform", "checkForPlatform");
        });
        tasks.named("buildPlatformIndependent", task -> {
            task.dependsOn("assemblePlatformIndependent", "checkPlatformIndependent");
        });
        tasks.named("buildCombinePlatform", task -> {
            task.dependsOn("assembleCombinePlatform", "checkCombinePlatform");
        });

        // The specialized "publish" tasks have the specialized "build" tasks as dependencies, just like the base
        tasks.named("publishForPlatform", task -> {
            task.dependsOn("buildForPlatform");
        });
        tasks.named("publishPlatformIndependent", task -> {
            task.dependsOn("buildPlatformIndependent");
        });
        tasks.named("publishCombinePlatform", task -> {
            task.dependsOn("buildCombinePlatform");
        });

        tasks.register("checkConsistency", task -> task.doFirst(it -> {
            it.getLogger().info("Checking that tasks aren't added to the base lifecycle tasks only");
            taskMap.keySet().forEach(kind -> {
                final Set<String> allLifecycleTasks = taskMap.entrySet().stream()
                        .flatMap(entry ->
                                Stream.concat(
                                        Stream.of(entry.getKey()),
                                        entry.getValue().keySet().stream()
                                                .map(s -> entry.getKey() + s)
                                )
                        )
                        .collect(Collectors.toSet());
                allLifecycleTasks.add("preCommit");

                final Set<String> taskToCheck = getDependencySet(tasks.getByName(kind), allLifecycleTasks);
                final Set<String> specializedTasks = ImmutableSet.<String>builder()
                        .addAll(getDependencySet(tasks.getByName(kind + "ForPlatform"), allLifecycleTasks))
                        .addAll(getDependencySet(tasks.getByName(kind + "PlatformIndependent"), allLifecycleTasks))
                        .addAll(getDependencySet(tasks.getByName(kind + "CombinePlatform"), allLifecycleTasks))
                        .build();

                final Sets.SetView<String> dependenciesNotInSpecialized = Sets.difference(taskToCheck, specializedTasks);
                if (!dependenciesNotInSpecialized.isEmpty()) {
                    throw new GradleException("Dependencies '" + dependenciesNotInSpecialized + "' for " + kind +
                            " isn't added to any platform specialized lifecycle tasks. " +
                            "Please add it to " + kind + "ForPlatform, " + kind + "PlatformIndependent " +
                            "or " + kind + "ALl"
                    );
                }
            });
        }));

        tasks.named("checkPlatformIndependent", task -> task.dependsOn("checkConsistency"));
    }

    @NotNull
    private Set<String> getDependencySet(Task taskToCheck, Set<String> filter) {
        return taskToCheck.getDependsOn().stream()
                .flatMap(dep -> getDependentTaskName(taskToCheck, dep))
                .filter(each -> !filter.contains(each))
                .collect(Collectors.toSet());
    }

    @NotNull
    @SuppressWarnings("rawtypes")
    private Stream<String> getDependentTaskName(Task taskTocheck, Object dep) {
        final String depTaskName;
        if ((dep instanceof Task) || (dep instanceof TaskProvider) || (dep instanceof String)) {
            if (dep instanceof Task) {
                depTaskName = ((Task) dep).getName();
            } else if (dep instanceof String) {
                depTaskName = (String) dep;
            } else {
                // This call can lead to tasks being created which can race with similar calls elsewhere, so we protect
                // against ConcurrentModificationException by retrying
                depTaskName = RetryUtils.retry(() -> {
                            final Object provided = ((TaskProvider) dep).get();
                            return ((Task) provided).getName();
                        })
                        .maxAttempt(3)
                        .execute();
            }
        } else {
            if (dep instanceof TaskDependency) {
                // This is used by assemble to auto view task dependencies, unlikely to be something manual
                return ((TaskDependency) dep).getDependencies(taskTocheck).stream()
                        .map(Task::getName);
            }
            if (dep instanceof NamedDomainObjectProvider) {
                return Stream.of(((NamedDomainObjectProvider) dep).getName());
            }
            throw new GradleException("Invalid dependency '" + dep + "' for " + taskTocheck + "(" + dep.getClass() + ")");
        }
        return Stream.of(depTaskName);
    }
}
