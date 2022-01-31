package co.elastic.cloud.gradle.dockerbase;

import co.elastic.cloud.gradle.docker.DockerPluginConventions;
import co.elastic.cloud.gradle.docker.manifest_tool.ManifestToolPlugin;
import co.elastic.cloud.gradle.util.Architecture;
import co.elastic.cloud.gradle.util.GradleUtils;
import org.gradle.api.Action;
import org.gradle.api.Plugin;
import org.gradle.api.Project;
import org.gradle.api.Task;
import org.gradle.api.provider.Provider;
import org.gradle.api.tasks.TaskProvider;

import java.lang.String;
import java.util.Set;
import java.util.concurrent.Callable;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.Stream;

public class DockerComponentPlugin implements Plugin<Project> {

    public static final String LOCAL_IMPORT_TASK_NAME = "dockerComponentImageLocalImport";

    @Override
    public void apply(Project project) {
        project.getPluginManager().apply(ManifestToolPlugin.class);


        final String localImportTag = DockerPluginConventions.localImportImageTag(project);
        final TaskProvider<DockerComponentLocalImport> localImport = project.getTasks().register(LOCAL_IMPORT_TASK_NAME, DockerComponentLocalImport.class);

        TaskProvider<ComponentBuildTask> dockerComponentImageBuild = project.getTasks().register(
                "dockerComponentImageBuild",
                ComponentBuildTask.class,
                // This is again a hack, because we don't have the DSL on a project extensions, so we need to make sure
                // that import task is registered with the copy specs from the DSL and maps them as inputs. The only
                // way we found that to work if it happens before the builds task is evaluated, thus we have it here
                DockerPluginConventions.mapCopySpecToTaskInputs(localImport)
        );
        // Skip component image build since these require the base images to have build in CI
        dockerComponentImageBuild.configure(task -> task.onlyIf(t -> GradleUtils.isCi()));

        localImport.configure(task -> {
            task.getTag().set(localImportTag);
            task.getInstructions().set(dockerComponentImageBuild.flatMap(ComponentBuildTask::getInstructions));
            task.doFirst("create layers from build task", new Action<Task>() {
                @Override
                public void execute(Task t) {
                    // This is a wired interaction between tasks. It's clear that we have a DSL for specifying the docker image
                    // and multiple tasks that do different operations with it so a better model would be to have the DSL
                    // as a project extensions rather than attached to the task, but that's too bug of a change to tackle
                    // as of this writing
                    dockerComponentImageBuild.get().createLayersDir();
                }
            });
            // To make sure that everything can indeed be synced we must inherit the dependencies of the build task
            task.dependsOn((Callable<Object>) () -> dockerComponentImageBuild.get().getDependsOn());
        });


        project.getTasks().register("dockerComponentImageClean", DockerLocalCleanTask.class, task -> {
            task.getImageTag().set(localImportTag);
        });
        project.getTasks().named("clean", clean->clean.dependsOn("dockerComponentImageClean"));

        TaskProvider<ComponentPushTask> dockerComponentImagePush = project.getTasks().register(
                "dockerComponentImagePush",
                ComponentPushTask.class
        );
        dockerComponentImagePush.configure(task -> {
            task.dependsOn(dockerComponentImageBuild);
            task.getImageArchive().set(
                    dockerComponentImageBuild.flatMap(ComponentBuildTask::getImageArchive)
            );
            task.getCreatedAtFiles().set(
                    dockerComponentImageBuild.flatMap(ComponentBuildTask::getCreatedAtFile)
            );
        });

        TaskProvider<PushManifestListTask> pushManifestList = project.getTasks().register(
                "pushManifestList",
                PushManifestListTask.class
        );
        pushManifestList.configure(task -> {
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
