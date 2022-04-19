package co.elastic.gradle.dockerbase;

import co.elastic.gradle.docker.base.DockerLocalCleanTask;
import co.elastic.gradle.lifecycle.LifecyclePlugin;
import co.elastic.gradle.lifecycle.MultiArchLifecyclePlugin;
import co.elastic.gradle.utils.Architecture;
import co.elastic.gradle.utils.GradleUtils;
import co.elastic.gradle.utils.docker.instruction.From;
import co.elastic.gradle.utils.docker.instruction.FromLocalImageBuild;
import org.gradle.api.Plugin;
import org.gradle.api.Project;
import org.gradle.api.Task;
import org.gradle.api.artifacts.Configuration;
import org.gradle.api.provider.Provider;
import org.gradle.api.provider.ProviderFactory;
import org.gradle.api.specs.Spec;
import org.gradle.api.tasks.TaskProvider;
import org.jetbrains.annotations.NotNull;

import javax.inject.Inject;


public abstract class DockerBaseImageBuildPlugin implements Plugin<Project> {

    public static final String BUILD_TASK_NAME = "dockerBaseImageBuild";
    public static final String LOCAL_IMPORT_TASK_NAME = "dockerBaseImageLocalImport";
    public static final String LOCKFILE_TASK_NAME = "dockerBaseImageLockfile";

    @Override
    public void apply(@NotNull Project target) {

        final BaseImageExtension extension = target.getExtensions().create("dockerBaseImage", BaseImageExtension.class);

        registerPullTask(target, extension);

        final Configuration configuration = target.getConfigurations().create("dockerEphemeral");

        TaskProvider<DockerBaseImageBuildTask> dockerBaseImageBuild = registerBuildTask(
                target,
                configuration,
                extension
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
                configuration
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
                    .map(FromLocalImageBuild::getOtherProjectPath)
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
            dockerBaseImageBuild.configure(task ->
                    ImageBuildable.assignCopySpecs(extension.getInstructions(), task)
            );
            dockerLockfileTask.configure(task ->
                    ImageBuildable.assignCopySpecs(extension.getInstructions(), task)
            );
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
                    task.getMirrorRepositories().set(extension.getMirrorRepositories());
                    task.getLockFileLocation().set(extension.getLockFileLocation());
                    task.getDockerEphemeralMount().set(extension.getDockerEphemeralMount());
                    task.getInputInstructions().set(extension.getInstructions());
                    task.onlyIf(runningOnSupportedArchitecture(extension));
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
                    extension.getDockerTagPrefix().map(value ->
                            value + "/" +
                            target.getName() + "-" + Architecture.current().dockerName() +
                            ":" + target.getVersion()
                    )
            );
            task.getCreatedAt().set(dockerBaseImageBuild.flatMap(DockerBaseImageBuildTask::getCreatedAt));
            task.onlyIf(runningOnSupportedArchitecture(extension));
        });
        MultiArchLifecyclePlugin.publishForPlatform(target, dockerBaseImagePush);
        return dockerBaseImagePush;
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
        return extension.getDockerTagLocalPrefix().map( value -> value + "/" +
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
    private TaskProvider<DockerBaseImageBuildTask> registerBuildTask(@NotNull Project target, Configuration dockerEphemeral, BaseImageExtension extension) {
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
                            getProviderFactory().provider(() -> extension.getInstructions().stream()
                                    .filter(each -> each instanceof From)
                                    .map(each -> ((From) each))
                                    .findAny()
                                    .orElseThrow(() -> new IllegalStateException("Can't find an image to pull. This is a bug."))
                                    .getReference())
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
