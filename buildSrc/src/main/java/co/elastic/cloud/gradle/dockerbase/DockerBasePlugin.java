package co.elastic.cloud.gradle.dockerbase;

import co.elastic.cloud.gradle.docker.DockerPluginConventions;
import org.gradle.api.Plugin;
import org.gradle.api.Project;

import java.util.Collections;

public class DockerBasePlugin implements Plugin<Project> {

    public static final String BUILD_TASK_NAME = "dockerBaseImageBuild";

    @Override
    public void apply(Project project) {
        String projectTag = DockerPluginConventions.imageTagWithQualifier(project, "-base");

        DockerBaseBuildTask buildTask = project.getTasks().create(BUILD_TASK_NAME, DockerBaseBuildTask.class, projectTag);

        DockerContextBuilderTask contextBuilderTask = project.getTasks().create("dockerBaseImageContext", DockerContextBuilderTask.class);
        contextBuilderTask.from(buildTask);

        DockerBaseLocalImportTask localImport = project.getTasks().create("dockerBaseImageLocalImport", DockerBaseLocalImportTask.class, buildTask.getBuildContext());
        localImport.dependsOn(buildTask);

        DockerBasePushTask push = project.getTasks().create("dockerBaseImagePush", DockerBasePushTask.class, buildTask.getBuildContext());
        push.dependsOn(buildTask);

        project.getGradle().getTaskGraph().whenReady(graph -> {
            if(graph.hasTask(localImport)) {
                buildTask.noDaemonClean();
            }
        });
    }
}
