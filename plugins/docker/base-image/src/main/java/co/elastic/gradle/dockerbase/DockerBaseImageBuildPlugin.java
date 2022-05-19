package co.elastic.gradle.dockerbase;

import co.elastic.gradle.cli.jfrog.JFrogPlugin;
import co.elastic.gradle.docker.base.DockerLocalCleanTask;
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
import org.gradle.api.Action;
import org.gradle.api.Plugin;
import org.gradle.api.Project;
import org.gradle.api.Task;
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


public abstract class DockerBaseImageBuildPlugin implements Plugin<Project> {

    public static final String BUILD_TASK_NAME = "dockerBaseImageBuild";
    public static final String LOCAL_IMPORT_TASK_NAME = "dockerBaseImageLocalImport";
    public static final String LOCKFILE_TASK_NAME = "dockerBaseImageLockfile";

    @Override
    public void apply(@NotNull Project target) {

        final BaseImageExtension extension = target.getExtensions().create("dockerBaseImage", BaseImageExtension.class);

        final Configuration osPackageConfiguration = target.getConfigurations().create("_osPackageRepo");

        registerPullTask(target, extension);

        final Configuration dockerEphemeralConfiguration = target.getConfigurations().create("dockerEphemeral");

        TaskProvider<DockerBaseImageBuildTask> dockerBaseImageBuild = registerBuildTask(
                target,
                dockerEphemeralConfiguration,
                extension,
                osPackageConfiguration
        );

        TaskProvider<DockerLocalImportArchiveTask> dockerBaseImageLocalImport = registerLocalImportTask(
                target,
                extension,
                dockerBaseImageBuild
        );

        registerCleanTask(target, extension);

        registerPushTask(target, extension, dockerBaseImageBuild);

        final TaskProvider<DockerLockfileTask> dockerLockfileTask = registerLockfileTask(
                target,
                extension,
                dockerEphemeralConfiguration
        );

        GradleUtils.registerOrGet(target, "dockerBuild").configure(task -> {
            task.dependsOn(dockerBaseImageBuild);
            task.setGroup("containers");
        });
        GradleUtils.registerOrGet(target, "dockerLocalImport").configure(task -> {
            task.dependsOn(dockerBaseImageLocalImport);
            task.setGroup("containers");
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
                        dockerLockfileTask.configure(task -> {
                            task.dependsOn(projectPath + ":" + LOCAL_IMPORT_TASK_NAME);
                            task.mustRunAfter(projectPath + ":" + LOCKFILE_TASK_NAME);
                        });
                    });

            // assign copy specs to the build tasks to correctly evaluate build avoidance
            dockerBaseImageBuild.configure(task -> InstructionCopySpecMapper.assignCopySpecs(extension.getInstructions(), ((ImageBuildable) task).getRootCopySpec())
            );
            dockerLockfileTask.configure(task -> InstructionCopySpecMapper.assignCopySpecs(extension.getInstructions(), ((ImageBuildable) task).getRootCopySpec())
            );

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
                repo.patternLayout(config -> config.artifact("[organisation]/[module]_[revision]_[classifier].[ext]"));
                repo.content(content -> content.onlyForConfigurations(dockerEphemeralConfiguration.getName()));
                if (credentialsAction != null) {
                    repo.credentials(credentialsAction);
                }
            });

            final Path lockfilePath = RegularFileUtils.toPath(extension.getLockFileLocation());
            if (Files.exists(lockfilePath)) {
                try {
                    final BaseLockfile lockfile = BaseLockfile.parse(
                            Files.newBufferedReader(lockfilePath)
                    );
                    lockfile.getPackages().get(Architecture.current()).getPackages()
                            .stream()
                            // FIXME: Random filter just for testing
                            .filter(pkg -> pkg.name().contains("libsystemd"))
                            .forEach( pkg ->
                            {
                                final String type = switch (extension.getOSDistribution().get()) {
                                    case CENTOS -> "rpm:";
                                    case DEBIAN, UBUNTU -> "deb";
                                };
                                target.getDependencies().add(
                                        dockerEphemeralConfiguration.getName(),
                                        type + ":" + pkg.name() +
                                        ":" + pkg.getVersion() +
                                        ":" + pkg.getArchitecture() +
                                        "@" + type
                                );
                            }
                    );
                } catch (IOException e) {
                    throw new UncheckedIOException(e);
                }
            }
        });
    }

    private TaskProvider<DockerLockfileTask> registerLockfileTask(
            @NotNull Project target,
            BaseImageExtension extension,
            Configuration configuration
    ) {
        final TaskProvider<DockerLockfileTask> dockerBaseImageLockfile = target.getTasks().register(
                LOCKFILE_TASK_NAME,
                DockerLockfileTask.class,
                configuration
        );
        dockerBaseImageLockfile.configure(task -> {
                    task.setGroup("containers");
                    task.setDescription("Generates a new lockfile with the latest version of all packages");
                    task.getOSDistribution().set(extension.getOSDistribution());
                    task.getLockFileLocation().set(extension.getLockFileLocation());
                    task.getDockerEphemeralMount().set(extension.getDockerEphemeralMount());
                    task.getInputInstructions().set(extension.getInstructions());
                    task.getOsPackageRepository().set(extension.getOsPackageRepository());
                    task.getMirrorRepositories().set(extension.getMirrorRepositories());
                    // hard code Linux here, because we are using it inside a docker container
                    task.getJFrogCli().set(JFrogPlugin.getExecutable(target, OS.LINUX));
                }
        );
        return dockerBaseImageLockfile;
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
    private TaskProvider<DockerBaseImageBuildTask> registerBuildTask(@NotNull Project target, Configuration dockerEphemeral, BaseImageExtension extension, Configuration osPackageConfiguration) {
        TaskProvider<DockerBaseImageBuildTask> dockerBaseImageBuild = target.getTasks().register(
                BUILD_TASK_NAME,
                DockerBaseImageBuildTask.class,
                dockerEphemeral
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
            task.dependsOn(osPackageConfiguration);
        });
        MultiArchLifecyclePlugin.assembleForPlatform(target, dockerBaseImageBuild);
        return dockerBaseImageBuild;
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
