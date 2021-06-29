package co.elastic.cloud.gradle.dockerbase;

import co.elastic.cloud.gradle.docker.DockerPluginConventions;
import org.gradle.api.Plugin;
import org.gradle.api.Project;
import org.gradle.api.provider.Provider;
import org.gradle.api.tasks.TaskProvider;

import java.util.concurrent.Callable;

public class DockerComponentPlugin implements Plugin<Project> {

    public static final String LOCAL_IMPORT_TASK_NAME = "dockerComponentImageLocalImport";

    @Override
    public void apply(Project project) {
        Provider<ComponentBuildTask> dockerComponentImageBuild = project.getTasks().register(
                "dockerComponentImageBuild",
                ComponentBuildTask.class
        );

        TaskProvider<ComponentLocalImportTask> dockerComponentImageLocalImport = project.getTasks().register(
                LOCAL_IMPORT_TASK_NAME,
                ComponentLocalImportTask.class,
                dockerComponentImageBuild,
                DockerPluginConventions.localImportImageTag(project)
        );
        dockerComponentImageLocalImport.configure(task -> task.dependsOn(dockerComponentImageBuild));

        project.afterEvaluate(evaluatedProject ->
            dockerComponentImageLocalImport.configure(task ->
                  task.dependsOn((Callable) () ->
                          dockerComponentImageBuild.get()
                                  .getFromContext()
                                  .getProject().getTasks()
                                  .named(DockerBasePlugin.BUILD_TASK_NAME)
                  )
            )
        );

        TaskProvider<ComponentPushTask> dockerComponentImagePush = project.getTasks().register(
                "dockerComponentImagePush",
                ComponentPushTask.class,
                dockerComponentImageBuild
        );
        dockerComponentImagePush.configure(task -> task.dependsOn(dockerComponentImageBuild));

        project.getTasks().named("assembleCombinePlatform", task -> task.dependsOn(dockerComponentImageBuild));
        project.getTasks().named("publishCombinePlatform", task -> task.dependsOn(dockerComponentImagePush));
    }
}
