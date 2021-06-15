package co.elastic.cloud.gradle.dockerbase;

import co.elastic.cloud.gradle.docker.DockerBuildContext;
import co.elastic.cloud.gradle.docker.DockerPluginConventions;
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

        String projectTag = DockerPluginConventions.imageTagWithQualifier(project, "-base");

        final DockerBuildContext dockerBuildContext = new DockerBuildContext(project, BUILD_TASK_NAME);

        TaskProvider<BaseBuildTask> dockerBaseImageBuild = project.getTasks().register(
                BUILD_TASK_NAME,
                BaseBuildTask.class,
                projectTag,
                dockerBuildContext,
                dockerEphemeral
        );

        TaskProvider<BaseLocalImportTask> dockerBaseImageLocalImport = project.getTasks().register(
                LOCAL_IMPORT_TASK_NAME,
                BaseLocalImportTask.class,
                dockerBuildContext,
                DockerPluginConventions.localImportImageTag(project, "-base")
        );
        dockerBaseImageLocalImport.configure(task -> task.dependsOn(dockerBaseImageBuild));

        TaskProvider<BasePullTask> dockerBasePull = project.getTasks().register(
                "dockerBasePull",
                BasePullTask.class,
                dockerBaseImageBuild
        );

        TaskProvider<BasePushTask> dockerBaseImagePush = project.getTasks().register("dockerBaseImagePush", BasePushTask.class, dockerBuildContext, projectTag);
        dockerBaseImagePush.configure(task -> task.dependsOn(dockerBaseImageBuild));

        project.getTasks().named("assemblePlatformIndependent", task -> task.dependsOn(dockerBaseImageBuild));
        project.getTasks().named("publishPlatformIndependent", task -> task.dependsOn(dockerBaseImagePush));
        project.getTasks().named("resolveAllDependencies", task -> task.dependsOn(dockerBasePull));
    }
}
