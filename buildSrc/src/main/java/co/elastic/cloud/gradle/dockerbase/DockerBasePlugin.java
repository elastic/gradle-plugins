package co.elastic.cloud.gradle.dockerbase;

import co.elastic.cloud.gradle.docker.DockerPluginConventions;
import org.gradle.api.Plugin;
import org.gradle.api.Project;

public class DockerBasePlugin implements Plugin<Project> {

    public static final String BUILD_TASK_NAME = "dockerBaseImageBuild";

    @Override
    public void apply(Project project) {
        String projectTag = DockerPluginConventions.imageTagWithQualifier(project, "-base");

        BaseBuildTask buildTask = project.getTasks().create(BUILD_TASK_NAME, BaseBuildTask.class, projectTag);

        BaseBuildTask.ContextBuilder contextBuilderTask = project.getTasks().create("dockerBaseImageContext", BaseBuildTask.ContextBuilder.class);
        contextBuilderTask.setFrom(buildTask);
        buildTask.dependsOn(contextBuilderTask);

        BaseLocalImportTask localImport = project.getTasks().create("dockerBaseImageLocalImport", BaseLocalImportTask.class, buildTask.getBuildContext(), DockerPluginConventions.localImportImageTag(project, "-base"));
        localImport.dependsOn(buildTask);

        BasePushTask push = project.getTasks().create("dockerBaseImagePush", BasePushTask.class, buildTask.getBuildContext(), projectTag);
        push.dependsOn(buildTask);

        project.getGradle().getTaskGraph().whenReady(graph -> {
            if(graph.hasTask(localImport)) {
                buildTask.noDaemonClean();
            }
        });
    }
}
