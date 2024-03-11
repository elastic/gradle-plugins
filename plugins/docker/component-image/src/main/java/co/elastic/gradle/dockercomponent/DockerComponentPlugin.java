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
package co.elastic.gradle.dockercomponent;

import co.elastic.gradle.cli.manifest.ManifestToolPlugin;
import co.elastic.gradle.dockerbase.DockerLocalCleanTask;
import co.elastic.gradle.lifecycle.LifecyclePlugin;
import co.elastic.gradle.lifecycle.MultiArchLifecyclePlugin;
import co.elastic.gradle.snyk.SnykCLIExecTask;
import co.elastic.gradle.snyk.SnykPlugin;
import co.elastic.gradle.utils.Architecture;
import co.elastic.gradle.utils.GradleUtils;
import co.elastic.gradle.utils.docker.InstructionCopySpecMapper;
import org.gradle.api.Plugin;
import org.gradle.api.Project;
import org.gradle.api.tasks.TaskProvider;

import java.util.Arrays;
import java.util.function.Function;
import java.util.stream.Collectors;

public class DockerComponentPlugin implements Plugin<Project> {

    public static final String LOCK_FILE_TASK_NAME = "dockerComponentLockFile";

    @Override
    public void apply(Project target) {
        target.getPluginManager().apply(ManifestToolPlugin.class);
        target.getPluginManager().apply(SnykPlugin.class);

        final ComponentImageBuildExtension extension = target.getExtensions()
                .create("dockerComponentImage", ComponentImageBuildExtension.class);

        final TaskProvider<ComponentPullTask> dockerComponentPull = target.getTasks().register(
                "dockerComponentPull",
                ComponentPullTask.class,
                task -> {
                    task.getLockfileLocation().set(extension.getLockFileLocation());
                }
        );
        LifecyclePlugin.resolveAllDependencies(target, dockerComponentPull);

        target.getTasks().register(
                LOCK_FILE_TASK_NAME,
                ComponentLockfileTask.class,
                task -> {
                    task.getInstructions().set(extension.getInstructions());
                    task.getLockFileLocation().set(extension.getLockFileLocation());
                }
        );

        TaskProvider<ComponentBuildTask> dockerComponentImageBuild = target.getTasks().register(
                "dockerComponentImageBuild",
                ComponentBuildTask.class,
                task -> {
                    task.getInstructions().set(extension.getInstructions());
                    task.getLockFileLocation().set(extension.getLockFileLocation());
                    task.getMaxOutputSizeMB().set(extension.getMaxOutputSizeMB());
                }
        );

        final TaskProvider<DockerComponentLocalImport> localImport = target.getTasks().register(
                "dockerComponentImageLocalImport",
                DockerComponentLocalImport.class,
                task -> {
                    task.getTag().set(
                            extension.getDockerTagLocalPrefix()
                                    .map(prefix -> prefix + "/" + target.getName() + ":latest")
                    );
                    task.getInstructions().set(extension.getInstructions());
                    task.getLockFileLocation().set(extension.getLockFileLocation());
                }
        );

        final TaskProvider<DockerLocalCleanTask> dockerComponentImageClean = target.getTasks().register(
                "dockerComponentImageClean",
                DockerLocalCleanTask.class,
                task -> {
                    task.getImageTag().set(localImport.flatMap(DockerComponentLocalImport::getTag));
                });

        LifecyclePlugin.clean(target, dockerComponentImageClean);

        TaskProvider<ComponentPushTask> dockerComponentImagePush = target.getTasks().register(
                "dockerComponentImagePush",
                ComponentPushTask.class,
                task -> {
                    task.dependsOn(dockerComponentImageBuild);
                    task.getImageArchive().set(
                            dockerComponentImageBuild.flatMap(ComponentBuildTask::getImageArchive)
                    );
                    task.getCreatedAtFiles().set(
                            dockerComponentImageBuild.flatMap(ComponentBuildTask::getCreatedAtFile)
                    );
                    task.getTags().set(
                            extension.getDockerTagPrefix().flatMap(prefix ->
                                    extension.getInstructions().map(instructions ->
                                            instructions.keySet().stream()
                                                    .collect(Collectors.toMap(
                                                            Function.identity(),
                                                            (Architecture arch) -> prefix + "/" + target.getName() +
                                                                                   ":" + target.getVersion() + "-" +
                                                                                   arch.dockerName())
                                                    ))
                            ));
                }
        );

        TaskProvider<PushManifestListTask> pushManifestList = target.getTasks().register(
                "pushManifestList",
                PushManifestListTask.class,
                task -> {
                    task.dependsOn(dockerComponentImagePush);
                    task.getArchitectureTags().set(
                            dockerComponentImagePush.flatMap(ComponentPushTask::getTags)
                    );
                    task.getTag().set(
                            extension.getDockerTagPrefix()
                                    .map(prefix -> prefix + "/" + target.getName() + ":" + target.getVersion())
                    );
                }
        );

        final TaskProvider<SnykCLIExecTask> dockerComponentImageScanLocal = target.getTasks().register(
                "dockerComponentImageScanLocal",
                SnykCLIExecTask.class,
                task -> {
                    task.setGroup("security");
                    task.setDescription("Runs a snyk test on the resulting locally imported image." +
                                        " The task fails if vulnerabilities are discovered.");
                    task.dependsOn(localImport);
                    task.doFirst(t -> task.setArgs(Arrays.asList("container", "test", localImport.get().getTag().get())));
                    // snyk only scans the image of the platform it's running on and would fail if one is not available.
                    // luckily a non-existing image can't have any vulnerabilities, so we can just skip it
                    task.onlyIf(
                            unsused -> dockerComponentImageBuild.get().getInstructions().keySet().get()
                                    .contains(Architecture.current())
                    );
                });

        final TaskProvider<SnykCLIExecTask> dockerComponentImageScan = target.getTasks().register(
                "dockerComponentImageScan",
                SnykCLIExecTask.class,
                task -> {
                    task.setGroup("security");
                    task.setDescription(
                            "Runs Snyk monitor on the resulting image from the container registry. " +
                            "Does not depend on pushing the image, instead assumes that this has already happened." +
                            "The task creates a report in Snyk and alwasy suceeds."
                    );
                    task.doFirst(t ->
                            task.setArgs(Arrays.asList("container", "monitor", pushManifestList.get().getTag().get()))
                    );
                    task.onlyIf(
                            unused -> dockerComponentImageBuild.get().getInstructions().keySet().get()
                                    .contains(Architecture.current())
                    );
                });

        GradleUtils.registerOrGet(target, "dockerBuild").configure(task ->
                task.dependsOn(dockerComponentImageBuild)
        );
        GradleUtils.registerOrGet(target, "dockerLocalImport").configure(task ->
                task.dependsOn(localImport)
        );

        MultiArchLifecyclePlugin.assembleCombinePlatform(target, dockerComponentImageBuild);
        MultiArchLifecyclePlugin.publishCombinePlatform(target, pushManifestList);

        LifecyclePlugin.securityScan(target, dockerComponentImageScan);

        target.afterEvaluate(p -> {
            // assign copy specs to the build tasks to correctly evaluate build avoidance
            dockerComponentImageBuild.configure(task ->
                    extension.getInstructions().get().forEach((arch, instructions) ->
                            InstructionCopySpecMapper.assignCopySpecs(instructions, task.rootCopySpec)
                    )
            );
            localImport.configure(task ->
                    extension.getInstructions().get().forEach((arch, instructions) ->
                            InstructionCopySpecMapper.assignCopySpecs(instructions, task.rootCopySpec)
                    )
            );
        });
    }
}
