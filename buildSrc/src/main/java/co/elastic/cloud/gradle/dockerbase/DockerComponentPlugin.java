package co.elastic.cloud.gradle.dockerbase;

import co.elastic.cloud.gradle.docker.DockerPluginConventions;
import co.elastic.cloud.gradle.util.Architecture;
import org.gradle.api.Plugin;
import org.gradle.api.Project;
import org.gradle.api.provider.Provider;
import org.gradle.api.tasks.TaskProvider;

import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.Stream;

public class DockerComponentPlugin implements Plugin<Project> {

    public static final String LOCAL_IMPORT_TASK_NAME = "dockerComponentImageLocalImport";

    @Override
    public void apply(Project project) {
        final Architecture currentArch = Architecture.current();

        Provider<ComponentBuildTask> dockerComponentImageBuild = project.getTasks().register(
                "dockerComponentImageBuild",
                ComponentBuildTask.class
        );

        final TaskProvider<DockerLocalImportTask> localImport = project.getTasks().register(LOCAL_IMPORT_TASK_NAME, DockerLocalImportTask.class);
        localImport.configure( task -> {
            task.dependsOn(dockerComponentImageBuild);
            task.getTag().set(
                    DockerPluginConventions.localImportImageTag(project)
            );
            task.getImageArchive().set(dockerComponentImageBuild.flatMap(build -> build.getImageArchive().getting(currentArch)));
            task.getImageId().set(
                    dockerComponentImageBuild.flatMap(build -> build.getImageId().map(map -> map.get(currentArch)))
            );
        });

        TaskProvider<ComponentPushTask> dockerComponentImagePush = project.getTasks().register(
                "dockerComponentImagePush",
                ComponentPushTask.class
        );
        dockerComponentImagePush.configure(task-> {
            task.dependsOn(dockerComponentImageBuild);
            task.getImageArchive().set(
                    dockerComponentImageBuild.flatMap(ComponentBuildTask::getImageArchive)
            );
            task.getTags().set(
                    Stream.of(Architecture.values())
                    .collect(Collectors.toMap(
                            Function.identity(),
                            architecture -> DockerPluginConventions.componentImageTagWithPlatform(project, architecture)
                    ))
            );
        });

        project.getTasks().named("assembleCombinePlatform", task -> task.dependsOn(dockerComponentImageBuild));
        project.getTasks().named("publishCombinePlatform", task -> task.dependsOn(dockerComponentImagePush));
    }
}
