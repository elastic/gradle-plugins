package co.elastic.cloud.gradle.dockerbase;

import co.elastic.cloud.gradle.docker.DockerPluginConventions;
import org.gradle.api.Plugin;
import org.gradle.api.Project;
import org.gradle.api.provider.Provider;

public class DockerComponentPlugin implements Plugin<Project> {

    @Override
    public void apply(Project project) {
        String projectTag = DockerPluginConventions.imageTag(project);

        Provider<ComponentBuildTask> buildTask = project.getTasks().register("dockerComponentImageBuild", ComponentBuildTask.class, projectTag);

        ComponentBuildTask.ContextBuilder contextBuilderTask = project.getTasks().create("dockerComponentImageContext", ComponentBuildTask.ContextBuilder.class);
        contextBuilderTask.setFrom(buildTask);

        buildTask.get().dependsOn(contextBuilderTask);


        ComponentLocalImportTask localImport = project.getTasks().create("dockerComponentImageLocalImport", ComponentLocalImportTask.class, buildTask, DockerPluginConventions.localImportImageTag(project));
        localImport.dependsOn(buildTask);

        ComponentPushTask push = project.getTasks().create("dockerComponentImagePush", ComponentPushTask.class, buildTask, projectTag);
        push.dependsOn(buildTask);
    }
}
