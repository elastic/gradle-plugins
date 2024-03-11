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
package co.elastic.gradle.lifecycle;

import co.elastic.gradle.utils.RetryUtils;
import org.gradle.api.*;
import org.gradle.api.plugins.ApplicationPlugin;
import org.gradle.api.plugins.JavaPlugin;
import org.gradle.api.tasks.TaskContainer;
import org.gradle.api.tasks.TaskDependency;
import org.gradle.api.tasks.TaskProvider;
import org.gradle.language.base.plugins.LifecycleBasePlugin;

import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static co.elastic.gradle.lifecycle.LifecyclePlugin.PUBLISH_TASK_NAME;

public class MultiArchLifecyclePlugin implements Plugin<Project> {
    @Override
    public void apply(Project target) {
        target.getPluginManager().apply(LifecyclePlugin.class);

        final TaskContainer tasks = target.getTasks();

        createMultiPlatformBuildLifecycle(tasks);

        tasks.named("checkPlatformIndependent", check -> check.dependsOn("preCommit"));

        // These binaries are platform dependent, we need to verify that they are available
        tasks.named("checkForPlatform", check -> check.dependsOn(LifecyclePlugin.SYNC_BIN_DIR_TASK_NAME));

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
        final Map<String, Map<String, String>> taskMap = Map.of(
                "check", Map.of(
                        "ForPlatform", "Run all platform specific verifications for the current platform only.",
                        "PlatformIndependent", "Run all platform independent verifications " +
                                               "(that don't depend on platform dependent tasks)",
                        "CombinePlatform", "Run all verifications for all platforms that can be ran in a platform independent way. " +
                                           "Assumes that platform specific artefacts for this version are already published.",
                        "", "Run verification for the current platform and that are platform independent." +
                            " Useful for local testing."
                ),
                "assemble", Map.of(
                        "ForPlatform", "Intended to create build artefacts, without running any tests for the current platform",
                        "PlatformIndependent", "Create platform independent build artefacts",
                        "CombinePlatform", "Create multi platform build artefacts, without running any tests.  " +
                                           "This includes the production artifacts and generating documentation for all supported platforms." +
                                           "Assumes that the platform specific artefacts were already published for this version.",
                        "", "Create platform specific artefacts for the current platform and emulate multi platform artefacts based on them." +
                            " Useful for local testing."
                ),
                "build", Map.of(
                        "ForPlatform", "Create artefacts and run all platform specific verifications for the current platform only.",
                        "PlatformIndependent", "Create artefacts and run all platform independent verifications " +
                                               "(that don't depend on platform dependent tasks)",
                        "CombinePlatform", "Create artefacts and  run all verifications for all platforms that can be ran in a platform independent way. " +
                                           "Assumes that platform specific artefacts for this version are already published.",
                        "", "Create artefacts and  run verification for the current platform and that are platform independent." +
                            " Useful for local testing."
                ),
                "publish", Map.of(
                        "ForPlatform", "Create, verify and publish all platform specific artefacts for the current platform only.",
                        "PlatformIndependent", "Create, verify and publish platform independent artefacts " +
                                               "(that don't depend on platform dependent tasks)",
                        "CombinePlatform", "Create, verify and publish artefacts multi platform artefacts. " +
                                           "Assumes that platform specific artefacts for this version are already published.",
                        "", "Create, verify  and run verification for the current platform and that are platform independent." +
                            " Useful for local testing."
                )
        );

        final Map<String, String> groupLookup = Map.of(
                "check", "verification",
                "assemble", "build"
        );

        taskMap.forEach((kind, descriptions) -> {
            descriptions.forEach((name, description) -> {
                final Action<Task> taskConfig = task -> {
                    if (!kind.equals("publish")) {
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
        tasks.named("buildForPlatform", task -> task.dependsOn("assembleForPlatform", "checkForPlatform"));
        tasks.named("buildPlatformIndependent", task -> task.dependsOn("assemblePlatformIndependent", "checkPlatformIndependent"));
        tasks.named("buildCombinePlatform", task -> task.dependsOn("assembleCombinePlatform", "checkCombinePlatform"));

        // The specialized "publish" tasks have the specialized "build" tasks as dependencies, just like the base
        tasks.named("publishForPlatform", task -> task.dependsOn("buildForPlatform"));
        tasks.named("publishPlatformIndependent", task -> task.dependsOn("buildPlatformIndependent"));
        tasks.named("publishCombinePlatform", task -> task.dependsOn("buildCombinePlatform"));

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

                final Set<String> taskToCheck = new HashSet<>(getDependencySet(tasks.getByName(kind), allLifecycleTasks));

                final Set<String> specializedTasks = new HashSet<>();
                specializedTasks.addAll(getDependencySet(tasks.getByName(kind + "ForPlatform"), allLifecycleTasks));
                specializedTasks.addAll(getDependencySet(tasks.getByName(kind + "PlatformIndependent"), allLifecycleTasks));
                specializedTasks.addAll(getDependencySet(tasks.getByName(kind + "CombinePlatform"), allLifecycleTasks));

                taskToCheck.removeAll(specializedTasks);

                if (!taskToCheck.isEmpty()) {
                    throw new GradleException("Dependencies '" + taskToCheck + "' for " + kind +
                                              " isn't added to any platform specialized lifecycle tasks. " +
                                              "Please add it to " + kind + "ForPlatform, " + kind + "PlatformIndependent " +
                                              "or " + kind + "ALl"
                    );
                }
            });
        }));

        tasks.named("checkPlatformIndependent", task -> task.dependsOn("checkConsistency"));
    }

    private Set<String> getDependencySet(Task taskToCheck, Set<String> filter) {
        return taskToCheck.getDependsOn().stream()
                .flatMap(dep -> getDependentTaskName(taskToCheck, dep))
                .filter(each -> !filter.contains(each))
                .collect(Collectors.toSet());
    }

    @SuppressWarnings("rawtypes")
    private Stream<String> getDependentTaskName(Task taskToCheck, Object dep) {
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
                return ((TaskDependency) dep).getDependencies(taskToCheck).stream()
                        .map(Task::getName);
            }
            if (dep instanceof NamedDomainObjectProvider) {
                return Stream.of(((NamedDomainObjectProvider) dep).getName());
            }
            throw new GradleException("Unsupported dependency '" + dep + "' for " + taskToCheck + "(" + dep.getClass() + ")");
        }
        return Stream.of(depTaskName);
    }

    private static void whenPluginAddedAddDependency(Project target, TaskProvider<? extends Task> dependency, String resolveAllDependenciesTaskName) {
        target.getPluginManager()
                .withPlugin(
                        "co.elastic.lifecycle-multi-arch",
                        p -> target.getTasks().named(resolveAllDependenciesTaskName, task -> task.dependsOn(dependency))
                );
    }

    public static void publishForPlatform(Project target, TaskProvider<? extends Task> dependency) {
        whenPluginAddedAddDependency(target, dependency, PUBLISH_TASK_NAME + "ForPlatform");
        LifecyclePlugin.publish(target, dependency);
    }

    public static void checkForPlatform(Project target, TaskProvider<? extends Task> dependency) {
        whenPluginAddedAddDependency(target, dependency, LifecycleBasePlugin.CHECK_TASK_NAME + "ForPlatform");
        LifecyclePlugin.check(target, dependency);
    }

    public static void assembleForPlatform(Project target, TaskProvider<? extends Task> dependency) {
        whenPluginAddedAddDependency(target, dependency, LifecycleBasePlugin.ASSEMBLE_TASK_NAME + "ForPlatform");
        LifecyclePlugin.assemble(target, dependency);
    }

    public static void publishPlatformIndependent(Project target, TaskProvider<? extends Task> dependency) {
        whenPluginAddedAddDependency(target, dependency, PUBLISH_TASK_NAME + "PlatformIndependent");
        LifecyclePlugin.publish(target, dependency);
    }

    public static void checkPlatformIndependent(Project target, TaskProvider<? extends Task> dependency) {
        whenPluginAddedAddDependency(target, dependency, LifecycleBasePlugin.CHECK_TASK_NAME + "PlatformIndependent");
        LifecyclePlugin.check(target, dependency);
    }

    public static void assemblePlatformIndependent(Project target, TaskProvider<? extends Task> dependency) {
        whenPluginAddedAddDependency(target, dependency, LifecycleBasePlugin.ASSEMBLE_TASK_NAME + "PlatformIndependent");
        LifecyclePlugin.assemble(target, dependency);
    }

    public static void publishCombinePlatform(Project target, TaskProvider<? extends Task> dependency) {
        whenPluginAddedAddDependency(target, dependency, PUBLISH_TASK_NAME + "");
        LifecyclePlugin.publish(target, dependency);
    }

    public static void checkCombinePlatform(Project target, TaskProvider<? extends Task> dependency) {
        whenPluginAddedAddDependency(target, dependency, LifecycleBasePlugin.CHECK_TASK_NAME + "");
        LifecyclePlugin.check(target, dependency);
    }

    public static void assembleCombinePlatform(Project target, TaskProvider<? extends Task> dependency) {
        whenPluginAddedAddDependency(target, dependency, LifecycleBasePlugin.ASSEMBLE_TASK_NAME + "");
        LifecyclePlugin.assemble(target, dependency);
    }

}
