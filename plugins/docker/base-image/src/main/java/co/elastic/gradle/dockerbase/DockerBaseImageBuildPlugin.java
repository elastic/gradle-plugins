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
package co.elastic.gradle.dockerbase;

import co.elastic.gradle.cli.jfrog.JFrogPlugin;
import co.elastic.gradle.dockerbase.lockfile.BaseLockfile;
import co.elastic.gradle.lifecycle.LifecyclePlugin;
import co.elastic.gradle.lifecycle.MultiArchLifecyclePlugin;
import co.elastic.gradle.utils.Architecture;
import co.elastic.gradle.utils.GradleUtils;
import co.elastic.gradle.utils.OS;
import co.elastic.gradle.utils.RegularFileUtils;
import co.elastic.gradle.utils.docker.InstructionCopySpecMapper;
import co.elastic.gradle.utils.docker.UnchangingContainerReference;
import co.elastic.gradle.utils.docker.instruction.From;
import co.elastic.gradle.utils.docker.instruction.FromLocalImageBuild;
import org.gradle.api.*;
import org.gradle.api.artifacts.Configuration;
import org.gradle.api.artifacts.repositories.IvyArtifactRepository;
import org.gradle.api.artifacts.repositories.PasswordCredentials;
import org.gradle.api.provider.Property;
import org.gradle.api.provider.Provider;
import org.gradle.api.provider.ProviderFactory;
import org.gradle.api.specs.Spec;
import org.gradle.api.tasks.TaskProvider;
import org.jetbrains.annotations.NotNull;

import javax.inject.Inject;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.net.MalformedURLException;
import java.net.URL;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.Locale;
import java.util.Map;


public abstract class DockerBaseImageBuildPlugin implements Plugin<Project> {

    public static final String BUILD_TASK_NAME = "dockerBaseImageBuild";
    public static final String LOCAL_IMPORT_TASK_NAME = "dockerBaseImageLocalImport";
    public static final String LOCKFILE_TASK_NAME = "dockerBaseImageLockfile";

    @Override
    public void apply(@NotNull Project target) {

        target.getPluginManager().apply(JFrogPlugin.class);

        final BaseImageExtension extension = target.getExtensions().create("dockerBaseImage", BaseImageExtension.class);

        final Configuration osPackageConfiguration = target.getConfigurations().create("_osPackageRepo");

        registerPullTask(target, extension);

        final Configuration dockerEphemeralConfiguration = target.getConfigurations().create("dockerEphemeral");

        TaskProvider<DockerBaseImageBuildTask> dockerBaseImageBuild = target.getTasks().register(
                BUILD_TASK_NAME,
                DockerBaseImageBuildTask.class
        );
        dockerBaseImageBuild.configure(task -> {
            task.getOSDistribution().set(extension.getOSDistribution());
            task.getMirrorRepositories().set(extension.getMirrorRepositories());
            task.getLockFile().set(extension.getLockFile());
            task.getDockerEphemeralMount().set(extension.getDockerEphemeralMount());
            task.getInputInstructions().set(extension.getInstructions());
            task.getMaxOutputSizeMB().set(extension.getMaxOutputSizeMB());
            task.getInputInstructions().set(extension.getInstructions());
            task.onlyIf(runningOnSupportedArchitecture(extension));
            task.getDockerEphemeralConfiguration().set(dockerEphemeralConfiguration);
            task.getOSPackagesConfiguration().set(osPackageConfiguration);
            task.dependsOn(osPackageConfiguration);
        });
        MultiArchLifecyclePlugin.assembleForPlatform(target, dockerBaseImageBuild);

        TaskProvider<DockerLocalImportArchiveTask> dockerBaseImageLocalImport = registerLocalImportTask(
                target,
                extension,
                dockerBaseImageBuild
        );

        registerCleanTask(target, extension);

        registerPushTask(target, extension, dockerBaseImageBuild);

        Arrays.stream(Architecture.values()).forEach( arch -> {
                    target.getTasks().register(
                            LOCKFILE_TASK_NAME + (
                                    arch.equals(Architecture.current()) ? "" : arch.dockerName()
                                    ),
                            DockerLockfileTask.class,
                            task -> task.getArchitecture().set(arch)
                    );
                }
        );

        target.getTasks().register(LOCKFILE_TASK_NAME + "AllWithEmulation",  task -> {
            task.dependsOn(target.getTasks().withType(DockerLockfileTask.class));
        });



        target.getTasks().withType(DockerLockfileTask.class).configureEach(task -> {
                    task.setGroup("containers");
                    task.setDescription("Generates a new lockfile with the latest version of all packages");
                    task.getOSDistribution().set(extension.getOSDistribution());
                    task.getLockFileLocation().set(extension.getLockFileLocation());
                    task.getDockerEphemeralMount().set(extension.getDockerEphemeralMount());
                    task.getInputInstructions().set(extension.getInstructions());
                    task.getOsPackageRepository().set(extension.getOsPackageRepository());
                    task.getMirrorRepositories().set(extension.getMirrorRepositories());
                    task.getDockerEphemeralConfiguration().set(dockerEphemeralConfiguration);
                    task.getOSPackagesConfiguration().set(osPackageConfiguration);
                    // hard code Linux here, because we are using it inside a docker container
                    task.getJFrogCli().set(JFrogPlugin.getExecutable(target, OS.LINUX));
                    task.onlyIf(runningOnSupportedArchitecture(extension));
                }
        );

        GradleUtils.registerOrGet(target, "dockerBuild").configure(task -> {
            task.dependsOn(dockerBaseImageBuild);
            task.setGroup("containers");
        });
        GradleUtils.registerOrGet(target, "dockerLocalImport").configure(task -> {
            task.dependsOn(dockerBaseImageLocalImport);
            task.setGroup("containers");
        });

        target.getGradle().getTaskGraph().whenReady(graph -> {
            final String separator = target.getPath().endsWith(":") ? "" : ":";
            final String lockfileTaskPath = target.getPath() + separator + LOCKFILE_TASK_NAME;
            final String buildTaskPath = target.getPath() + separator + BUILD_TASK_NAME;
            if (graph.hasTask(lockfileTaskPath) && graph.hasTask(buildTaskPath)
            ) {
                throw new GradleException("Generating the lockfile and building an image using it in the same invocation" +
                                          " is not supported. The lockfile should be generated and checked in. It can be " +
                                          "re-generated periodically to update dependencies, but doing at the same time" +
                                          "defeats the purpose of having a lockfile.");
            }
        });

        target.afterEvaluate(p -> {
            // Assign dependencies to local image builds in other projects
            extension.getInstructions().stream()
                    .filter(each -> each instanceof FromLocalImageBuild)
                    .map(each -> (FromLocalImageBuild) each)
                    .map(FromLocalImageBuild::otherProjectPath)
                    .forEach(projectPath -> {
                        dockerBaseImageBuild.configure(task ->
                                task.dependsOn(projectPath + ":" + LOCAL_IMPORT_TASK_NAME)
                        );
                        // We don't force lock-files to be updated at the same time, but if they are, we need to order
                        // them for correct results
                        target.getTasks().withType(DockerLockfileTask.class).configureEach(task -> {
                            task.dependsOn(projectPath + ":" + LOCAL_IMPORT_TASK_NAME);
                            task.mustRunAfter(projectPath + ":" + LOCKFILE_TASK_NAME);
                        });
                    });

            // assign copy specs to the build tasks to correctly evaluate build avoidance
            dockerBaseImageBuild.configure(task ->
                    InstructionCopySpecMapper.assignCopySpecs(
                            extension.getInstructions(), ((ImageBuildable) task).getRootCopySpec()
                    )
            );
            target.getTasks().withType(DockerLockfileTask.class).configureEach(task ->
                    InstructionCopySpecMapper.assignCopySpecs(
                            extension.getInstructions(),
                            ((ImageBuildable) task).getRootCopySpec())
            );

            if (extension.getOsPackageRepository().isPresent()) {
                final URL repoUrl = extension.getOsPackageRepository().get();
                Action<? super PasswordCredentials> credentialsAction =
                        (repoUrl.getUserInfo() != null) ?
                                config -> {
                                    final String[] userinfo = repoUrl.getUserInfo().split(":");
                                    config.setUsername(userinfo[0]);
                                    config.setPassword(userinfo[1]);
                                } : null;

                target.getRepositories().ivy(repo -> {
                    repo.setName(repoUrl.getHost() + "/" + repoUrl.getPath());
                    repo.metadataSources(IvyArtifactRepository.MetadataSources::artifact);
                    try {
                        repo.setUrl(new URL(repoUrl.toString().replace(repoUrl.getUserInfo() + "@", "")));
                    } catch (MalformedURLException e) {
                        throw new IllegalStateException(e);
                    }
                    // We don't use [ext] and add extension to classifier instead since Gradle doesn't allow it to be empty and defaults to jar
                    repo.patternLayout(config -> config.artifact("[organisation]/[module]-[revision].[ext]"));
                    repo.content(content -> content.onlyForConfigurations(osPackageConfiguration.getName()));
                    if (credentialsAction != null) {
                        repo.credentials(credentialsAction);
                    }
                });
            }

            final Path lockfilePath = RegularFileUtils.toPath(extension.getLockFileLocation());
            if (Files.exists(lockfilePath)) {
                try {
                    final BaseLockfile lockfile = BaseLockfile.parse(
                            Files.newBufferedReader(lockfilePath)
                    );
                    lockfile.getPackages().get(Architecture.current()).getPackages()
                            .stream()
                            .forEach(pkg ->
                                    {
                                        final String type = extension.getOSDistribution().get()
                                                .name().toLowerCase(Locale.ROOT);
                                        final Map<String, String> dependencyNotation = Map.of(
                                                "group", type,
                                                "name", pkg.name(),
                                                // Gradle has trouble dealing with : in the version, so we rename the
                                                // packages to have . instead and use the same here
                                                "version", switch (extension.getOSDistribution().get()) {
                                                    case DEBIAN, UBUNTU -> pkg.version().replace(":", ".") +
                                                                           "-" + pkg.architecture();
                                                    case CENTOS -> pkg.version() + "-" +
                                                                   pkg.release() + "." +
                                                                   pkg.architecture();
                                                },
                                                "ext", switch (extension.getOSDistribution().get()) {
                                                    case DEBIAN, UBUNTU -> pkg.name().startsWith("__META__") ? "gz" : "deb";
                                                    case CENTOS -> pkg.name().startsWith("__META__") ? "tar" : "rpm";
                                                }
                                        );
                                        target.getDependencies().add(
                                                osPackageConfiguration.getName(),
                                                dependencyNotation
                                        );
                                    }
                            );
                } catch (IOException e) {
                    throw new UncheckedIOException(e);
                }
            }
        });
    }

    @NotNull
    private TaskProvider<DockerPushTask> registerPushTask(
            @NotNull Project target,
            BaseImageExtension extension,
            TaskProvider<DockerBaseImageBuildTask> dockerBaseImageBuild
    ) {
        TaskProvider<DockerPushTask> dockerBaseImagePush = target.getTasks().register(
                "dockerBaseImagePush",
                DockerPushTask.class
        );
        dockerBaseImagePush.configure(task -> {
            task.getImageArchive().set(
                    dockerBaseImageBuild.flatMap(DockerBaseImageBuildTask::getImageArchive)
            );
            task.getTag().set(
                    pushedTagConvention(target, Architecture.current())
            );
            task.getCreatedAt().set(dockerBaseImageBuild.flatMap(DockerBaseImageBuildTask::getCreatedAt));
            task.onlyIf(runningOnSupportedArchitecture(extension));
        });
        MultiArchLifecyclePlugin.publishForPlatform(target, dockerBaseImagePush);
        return dockerBaseImagePush;
    }

    @NotNull
    public static Provider<String> pushedTagConvention(@NotNull Project target, Architecture current) {
        Property<String> dockerTagPrefix = target.getExtensions()
                .getByType(BaseImageExtension.class)
                .getDockerTagPrefix();

        return dockerTagPrefix.map(value ->
                {
                    return value + "/" +
                           target.getName() + "-" + current.dockerName() +
                           ":" + target.getVersion();
                }
        );
    }

    private void registerCleanTask(@NotNull Project target, BaseImageExtension extension) {
        final TaskProvider<DockerLocalCleanTask> dockerBaseImageClean = target.getTasks().register(
                "dockerBaseImageClean",
                DockerLocalCleanTask.class,
                task -> {
                    task.getImageTag().set(localImportTag(target, extension));
                    task.onlyIf(runningOnSupportedArchitecture(extension));
                }
        );
        LifecyclePlugin.clean(target, dockerBaseImageClean);
    }

    private Provider<String> localImportTag(Project target, BaseImageExtension extension) {
        return extension.getDockerTagLocalPrefix().map(value -> value + "/" +
                                                                target.getName() + "-base" +
                                                                ":latest"
        );
    }

    @NotNull
    private TaskProvider<DockerLocalImportArchiveTask> registerLocalImportTask(@NotNull Project target, BaseImageExtension extension, TaskProvider<DockerBaseImageBuildTask> dockerBaseImageBuild) {
        TaskProvider<DockerLocalImportArchiveTask> dockerBaseImageLocalImport = target.getTasks().register(
                LOCAL_IMPORT_TASK_NAME,
                DockerLocalImportArchiveTask.class,
                task -> {
                    task.getTag().set(localImportTag(target, extension));
                    task.getImageArchive().set(
                            dockerBaseImageBuild.flatMap(DockerBaseImageBuildTask::getImageArchive)
                    );
                    task.getImageId().set(
                            dockerBaseImageBuild.flatMap(DockerBaseImageBuildTask::getImageId)
                    );
                    task.onlyIf(runningOnSupportedArchitecture(extension));
                }
        );
        return dockerBaseImageLocalImport;
    }

    @NotNull
    private TaskProvider<BasePullTask> registerPullTask(@NotNull Project target, BaseImageExtension extension) {
        TaskProvider<BasePullTask> dockerBasePull = target.getTasks().register(
                "dockerBasePull",
                BasePullTask.class,
                task -> {
                    task.getTag().set(
                            extension.getInstructions().stream()
                                    .filter(each -> each instanceof From)
                                    .map(each -> ((From) each))
                                    .map(from -> extension.getLockFile().map(lockfile -> {
                                        if (lockfile.image() != null &&
                                            lockfile.getImage().get(Architecture.current()) != null) {
                                            final UnchangingContainerReference ref = lockfile.image().get(Architecture.current());
                                            return new From(getProviderFactory().provider(() -> String.format("%s:%s@%s",
                                                    ref.getRepository(), ref.getTag(), ref.getDigest()
                                            )));
                                        } else {
                                            return from;
                                        }
                                    }))
                                    .findAny()
                                    .map(provider -> provider.flatMap(From::getReference))
                                    .orElse(getProviderFactory().provider(() -> ""))
                    );
                    task.onlyIf(t -> extension.getInstructions().stream()
                            .anyMatch(each -> each instanceof From)
                    );
                    task.onlyIf(runningOnSupportedArchitecture(extension));
                }
        );
        LifecyclePlugin.resolveAllDependencies(target, dockerBasePull);
        return dockerBasePull;
    }

    @Inject
    protected abstract ProviderFactory getProviderFactory();

    @NotNull
    private Spec<Task> runningOnSupportedArchitecture(BaseImageExtension extension) {
        return unused -> extension.getPlatforms().get().contains(Architecture.current());
    }


}
