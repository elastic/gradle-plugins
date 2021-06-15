package co.elastic.cloud.gradle.dockerbase;

import co.elastic.cloud.gradle.docker.DockerPluginConventions;
import org.gradle.api.Plugin;
import org.gradle.api.Project;
import org.gradle.api.provider.Provider;
import org.gradle.api.tasks.TaskProvider;

public class DockerComponentPlugin implements Plugin<Project> {

    @Override
    public void apply(Project project) {
        String projectTag = DockerPluginConventions.imageTag(project);

        Provider<ComponentBuildTask> dockerComponentImageBuild = project.getTasks().register(
                "dockerComponentImageBuild",
                ComponentBuildTask.class,
                projectTag
        );

        TaskProvider<ComponentLocalImportTask> dockerComponentImageLocalImport = project.getTasks().register(
                "dockerComponentImageLocalImport",
                ComponentLocalImportTask.class,
                dockerComponentImageBuild,
                DockerPluginConventions.localImportImageTag(project)
        );
        dockerComponentImageLocalImport.configure(task -> task.dependsOn(dockerComponentImageBuild));

        TaskProvider<ComponentPushTask> dockerComponentImagePush = project.getTasks().register("dockerComponentImagePush", ComponentPushTask.class, dockerComponentImageBuild, projectTag);
        dockerComponentImagePush.configure(task -> task.dependsOn(dockerComponentImageBuild));

        project.getTasks().named("assembleCombinePlatform", task -> task.dependsOn(dockerComponentImageBuild));
        project.getTasks().named("publishCombinePlatform", task -> task.dependsOn(dockerComponentImagePush));
    }
}
