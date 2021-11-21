package co.elastic.cloud.gradle.dockerbase;

import co.elastic.cloud.gradle.docker.DockerPluginConventions;
import co.elastic.cloud.gradle.util.Architecture;
import co.elastic.cloud.gradle.util.GradleUtils;
import org.gradle.api.Plugin;
import org.gradle.api.Project;
import org.gradle.api.artifacts.Configuration;
import org.gradle.api.tasks.TaskProvider;
import org.jetbrains.annotations.NotNull;


public class DockerBasePlugin implements Plugin<Project> {

    public static final String BUILD_TASK_NAME = "dockerBaseImageBuild";
    public static final String LOCAL_IMPORT_TASK_NAME = "dockerBaseImageLocalImport";

    @Override
    public void apply(@NotNull Project project) {
        final Configuration dockerEphemeral = project.getConfigurations().create("dockerEphemeral");
        final Configuration repositoryEphemeral = project.getConfigurations().create("repositoryEphemeral");
        final Architecture currentArchitecture = Architecture.current();

        TaskProvider<BaseBuildTask> dockerBaseImageBuild = project.getTasks().register(
                BUILD_TASK_NAME,
                BaseBuildTask.class,
                dockerEphemeral,
                repositoryEphemeral
        );
        dockerBaseImageBuild.configure( task -> {
            task.onlyIf(unsued -> task.getSupportedPlatforms().contains(currentArchitecture));
        });

        TaskProvider<DockerLocalImportTask> dockerBaseImageLocalImport = project.getTasks().register(
                LOCAL_IMPORT_TASK_NAME,
                DockerLocalImportTask.class
        );
        dockerBaseImageLocalImport.configure(task -> {
            task.getTag().set(DockerPluginConventions.localImportImageTag(project));
            task.getImageArchive().set(
                    dockerBaseImageBuild.flatMap(BaseBuildTask::getImageArchive)
            );
            task.getImageId().set(
                    dockerBaseImageBuild.flatMap(BaseBuildTask::getImageId)
            );
            task.onlyIf(unsued -> dockerBaseImageBuild.get().getSupportedPlatforms().contains(currentArchitecture));
        });

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

        project.getTasks().named("assembleForPlatform", task -> task.dependsOn(dockerBaseImageBuild));
        project.getTasks().named("publishForPlatform", task -> task.dependsOn(dockerBaseImagePush));
        project.getTasks().named("resolveAllDependencies", task -> task.dependsOn(dockerBasePull));
    }

}
