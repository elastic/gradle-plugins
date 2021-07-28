package co.elastic.cloud.gradle.dockerbase;

import co.elastic.cloud.gradle.docker.DockerPluginConventions;
import co.elastic.cloud.gradle.docker.manifest_tool.ManifestToolPlugin;
import co.elastic.cloud.gradle.util.Architecture;
import co.elastic.cloud.gradle.util.GradleUtils;
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
        project.getPluginManager().apply(ManifestToolPlugin.class);

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
        });

        TaskProvider<PushManifestListTask> pushManifestList = project.getTasks().register(
                "pushManifestList",
                PushManifestListTask.class
        );
        pushManifestList.configure( task -> {
            task.dependsOn(dockerComponentImagePush);
            task.getArchitectureTags().set(
                    dockerComponentImagePush.flatMap(ComponentPushTask::getTags)
            );
            task.getTag().set(DockerPluginConventions.manifestListTag(project));
        });

        GradleUtils.registerOrGet(project, "dockerBuild").configure(task ->
                task.dependsOn(dockerComponentImageBuild)
        );
        GradleUtils.registerOrGet(project, "dockerLocalImport").configure(task ->
                task.dependsOn(localImport)
        );

        project.getTasks().named("assembleCombinePlatform", task -> task.dependsOn(dockerComponentImageBuild));
        project.getTasks().named("publishCombinePlatform", task -> task.dependsOn(pushManifestList));
    }
}
