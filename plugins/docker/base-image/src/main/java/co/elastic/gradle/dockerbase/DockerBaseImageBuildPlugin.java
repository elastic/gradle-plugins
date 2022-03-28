package co.elastic.gradle.dockerbase;

import co.elastic.gradle.docker.base.DockerLocalCleanTask;
import co.elastic.gradle.utils.Architecture;
import co.elastic.gradle.utils.GradleUtils;
import co.elastic.gradle.utils.docker.DockerPluginConventions;
import org.gradle.api.Plugin;
import org.gradle.api.Project;
import org.gradle.api.artifacts.Configuration;
import org.gradle.api.tasks.TaskProvider;
import org.jetbrains.annotations.NotNull;


public class DockerBaseImageBuildPlugin implements Plugin<Project> {

    public static final String BUILD_TASK_NAME = "dockerBaseImageBuild";
    public static final String LOCAL_IMPORT_TASK_NAME = "dockerBaseImageLocalImport";

    @Override
    public void apply(@NotNull Project project) {
        final Configuration dockerEphemeral = project.getConfigurations().create("dockerEphemeral");
        final Configuration repositoryEphemeral = project.getConfigurations().create("repositoryEphemeral");
        final Architecture currentArchitecture = Architecture.current();

        TaskProvider<DockerLockfileTaintTask> dockerBaseImageTaintLockfile = project.getTasks().register(
                "dockerBaseImageTaintLockfile",
                DockerLockfileTaintTask.class
        );

        TaskProvider<BaseBuildTask> dockerBaseImageBuild = project.getTasks().register(
                BUILD_TASK_NAME,
                BaseBuildTask.class,
                dockerEphemeral,
                repositoryEphemeral
        );
        dockerBaseImageBuild.configure(task -> {
            task.onlyIf(unsued -> task.getSupportedPlatforms().contains(currentArchitecture));
            task.mustRunAfter(dockerBaseImageTaintLockfile);
        });

        TaskProvider<DockerLocalImportArchiveTask> dockerBaseImageLocalImport = project.getTasks().register(
                LOCAL_IMPORT_TASK_NAME,
                DockerLocalImportArchiveTask.class
        );
        final String localImportTag = DockerPluginConventions.localImportImageTag(project, "base");
        dockerBaseImageLocalImport.configure(task -> {
            task.getTag().set(localImportTag);
            task.getImageArchive().set(
                    dockerBaseImageBuild.flatMap(BaseBuildTask::getImageArchive)
            );
            task.getImageId().set(
                    dockerBaseImageBuild.flatMap(BaseBuildTask::getImageId)
            );
            task.onlyIf(unsued -> dockerBaseImageBuild.get().getSupportedPlatforms().contains(currentArchitecture));
        });

        project.getTasks().register("dockerBaseImageClean", DockerLocalCleanTask.class, task -> {
            task.getImageTag().set(localImportTag);
        });
        project.getTasks().named("clean", clean -> clean.dependsOn("dockerBaseImageClean"));

        TaskProvider<BasePullTask> dockerBasePull = project.getTasks().register(
                "dockerBasePull",
                BasePullTask.class,
                dockerBaseImageBuild
        );
        dockerBasePull.configure(task -> {
            task.onlyIf(unsued -> dockerBaseImageBuild.get().getSupportedPlatforms().contains(currentArchitecture));
        });

        TaskProvider<DockerPushTask> dockerBaseImagePush = project.getTasks().register(
                "dockerBaseImagePush",
                DockerPushTask.class
        );
        dockerBaseImagePush.configure(task -> {
            task.getImageArchive().set(
                    dockerBaseImageBuild.flatMap(BaseBuildTask::getImageArchive)
            );
            task.getTag().set(DockerPluginConventions.baseImageTag(project, currentArchitecture));
            task.getCreatedAt().set(dockerBaseImageBuild.flatMap(BaseBuildTask::getCreatedAt));
            task.onlyIf(unsued -> dockerBaseImageBuild.get().getSupportedPlatforms().contains(currentArchitecture));
        });

        GradleUtils.registerOrGet(project, "dockerBuild").configure(task ->
                task.dependsOn(dockerBaseImageBuild)
        );
        GradleUtils.registerOrGet(project, "dockerLocalImport").configure(task ->
                task.dependsOn(dockerBaseImageLocalImport)
        );

        TaskProvider<DockerLockfileTask> dockerBaseImageLockfile = project.getTasks().register(
                "dockerBaseImageLockfile",
                DockerLockfileTask.class
        );
        dockerBaseImageLockfile.configure(task -> {
            task.getTag().set(localImportTag);
            task.getLockfileImage().set(dockerBaseImageBuild.flatMap(BaseBuildTask::getLockfileImage));
            task.dependsOn(dockerBaseImageTaintLockfile, dockerBaseImageLocalImport);
        });

        TaskProvider<DockerLockfileCheckTask> dockerBaseImageCheckLockfile = project.getTasks().register(
                "dockerBaseImageLockfileCheck",
                DockerLockfileCheckTask.class
        );

        project.getTasks().named("assembleForPlatform", task -> task.dependsOn(dockerBaseImageBuild));
        project.getTasks().named("publishForPlatform", task -> task.dependsOn(dockerBaseImagePush));
        project.getTasks().named("resolveAllDependencies", task -> task.dependsOn(dockerBasePull));
    }

}
