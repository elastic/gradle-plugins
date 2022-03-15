package co.elastic.cloud.gradle.dockerbase;

import co.elastic.cloud.gradle.docker.DockerPluginConventions;
import co.elastic.cloud.gradle.docker.manifest_tool.ManifestToolPlugin;
import co.elastic.cloud.gradle.snyk.SnykCLIExecTask;
import co.elastic.cloud.gradle.snyk.SnykCLIPlugin;
import co.elastic.cloud.gradle.util.Architecture;
import co.elastic.cloud.gradle.util.GradleUtils;
import co.elastic.cloud.gradle.util.StaticCliProvisionPlugin;
import org.gradle.api.Action;
import org.gradle.api.Plugin;
import org.gradle.api.Project;
import org.gradle.api.Task;
import org.gradle.api.provider.Provider;
import org.gradle.api.tasks.TaskProvider;

import java.lang.String;
import java.util.Arrays;
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
        project.getPluginManager().apply(SnykCLIPlugin.class);

        final String localImportTag = DockerPluginConventions.localImportImageTag(project);
        final String manifestPushTag = DockerPluginConventions.manifestListTag(project);

        final TaskProvider<DockerComponentLocalImport> localImport = project.getTasks().register(LOCAL_IMPORT_TASK_NAME, DockerComponentLocalImport.class);

        TaskProvider<ComponentBuildTask> dockerComponentImageBuild = project.getTasks().register(
                "dockerComponentImageBuild",
                ComponentBuildTask.class,
                // This is again a hack, because we don't have the DSL on a project extensions, so we need to make sure
                // that import task is registered with the copy specs from the DSL and maps them as inputs. The only
                // way we found that to work if it happens before the builds task is evaluated, thus we have it here
                DockerPluginConventions.mapCopySpecToTaskInputs(localImport)
        );

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
            task.setManifestTool(ManifestToolPlugin.getExecutable(task));
            task.dependsOn(dockerComponentImagePush);
            task.getArchitectureTags().set(
                    dockerComponentImagePush.flatMap(ComponentPushTask::getTags)
            );
            task.getTag().set(manifestPushTag);
        });

        final TaskProvider<SnykCLIExecTask> dockerComponentImageScanLocal = project.getTasks().register(
                "dockerComponentImageScanLocal",
                SnykCLIExecTask.class
        );
        dockerComponentImageScanLocal.configure(task -> {
            task.setGroup("security");
            task.setDescription("Runs a snyk test on the resulting locally imported image." +
                    " The task fails if voulnerabilitiues are discovered.");
            task.dependsOn(localImport);
            task.setArgs(Arrays.asList("container", "test", localImportTag));
            // snyk only scans the image of the platform it's running on and would fail if onw is not available.
            // luckily a non-existing image can't have any vulnerabilities, so we can just skip it
            task.onlyIf(
                    unsused -> dockerComponentImageBuild.get().getInstructions().keySet().get()
                            .contains(Architecture.current())
            );
        });

        final TaskProvider<SnykCLIExecTask> dockerComponentImageScan = project.getTasks().register(
                "dockerComponentImageScan",
                SnykCLIExecTask.class
        );
        dockerComponentImageScan.configure(task -> {
            task.setGroup("security");
            task.setDescription(
                    "Runs Snyk monitor on the resulting image from the container registry. " +
                    "Does not depend on pushing the image, instead assumes that this has already happened." +
                    "The task creates a report in Snyk and alwasy suceeds."
            );
            task.setArgs(Arrays.asList("container", "monitor", manifestPushTag));
            task.onlyIf(
                    unsused -> dockerComponentImageBuild.get().getInstructions().keySet().get()
                            .contains(Architecture.current())
            );
        });

        GradleUtils.registerOrGet(project, "dockerBuild").configure(task ->
                task.dependsOn(dockerComponentImageBuild)
        );
        GradleUtils.registerOrGet(project, "dockerLocalImport").configure(task ->
                task.dependsOn(localImport)
        );

        project.getTasks().named("assembleCombinePlatform", task -> task.dependsOn(dockerComponentImageBuild));
        project.getTasks().named("publishCombinePlatform", task -> task.dependsOn(pushManifestList));
        
        project.getTasks().named("securityScan", task -> task.dependsOn(dockerComponentImageScan));
    }
}
