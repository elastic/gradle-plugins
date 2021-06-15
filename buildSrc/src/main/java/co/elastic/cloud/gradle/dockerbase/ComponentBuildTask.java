package co.elastic.cloud.gradle.dockerbase;

import co.elastic.cloud.gradle.docker.DockerBuildContext;
import co.elastic.cloud.gradle.docker.action.JibActions;
import co.elastic.cloud.gradle.docker.build.DockerBuildInfo;
import co.elastic.cloud.gradle.util.RetryUtils;
import com.google.cloud.tools.jib.api.*;
import com.google.cloud.tools.jib.frontend.CredentialRetrieverFactory;
import com.google.gson.Gson;
import kotlin.Pair;
import org.gradle.api.Action;
import org.gradle.api.GradleException;
import org.gradle.api.Project;
import org.gradle.api.file.CopySpec;
import org.gradle.api.tasks.*;

import javax.inject.Inject;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.function.Supplier;

@CacheableTask
public class ComponentBuildTask extends Sync {

    private final List<JibInstruction> instructions;
    private final DockerBuildContext buildContext;
    private final String imageTag;
    private boolean createdLayers = false;

    @Inject
    public ComponentBuildTask(String imageTag) {
        this.imageTag = imageTag;
        this.instructions = new ArrayList<>();
        this.buildContext = new DockerBuildContext(getProject(), getName());
        setDestinationDir(buildContext.contextPath().toFile());
    }

    /*
     *
     * Input tracking
     *
     */

    @OutputDirectory
    public File getGeneratedImage() {
        return getBuildContext().jibApplicationLayerCachePath().toFile();
    }

    @Nested
    public List<JibInstruction> getInstructions() {
        return instructions;
    }

    /*
     *
     * Actions
     *
     */

    protected DockerBuildInfo build(String tag) {
        try {
            JibActions actions = new JibActions();

            DockerBuildInfo buildInfo = actions.buildTo(
                    buildContext,
                    instructions,
                    Containerizer.to(TarImage.at(buildContext.projectTarImagePath()).named(tag))
            );
            buildInfo.setTag(ImageReference.parse(tag).toString());
            saveDockerBuildInfo(buildInfo);
            return buildInfo;
        } catch (InvalidImageReferenceException e) {
            throw new GradleException("Invalid tag for local daemon import " + tag, e);

        }
    }

    protected void localImport(String tag) {
        maybeCreateLayerDirectories();
        try {
            ImageReference imageReference = ImageReference.parse(tag);
            JibActions actions = new JibActions();
            RetryUtils.retry(() -> actions.buildTo(
                    buildContext,
                    instructions,
                    Containerizer.to(DockerDaemonImage.named(imageReference)))
            )
                    .maxAttempt(3)
                    .exponentialBackoff(1000, 30000)
                    .onRetryError(e -> getLogger().warn("Error importing jib image", e))
                    .execute();
        } catch (InvalidImageReferenceException e) {
            throw new GradleException("Invalid tag for local daemon import " + tag, e);

        }
    }

    protected void push(String tag) {
        maybeCreateLayerDirectories();
        try {
            ImageReference imageReference = ImageReference.parse(tag);
            JibActions actions = new JibActions();
            RetryUtils.retry(() -> actions.buildTo(
                    buildContext,
                    instructions,
                    Containerizer.to(
                            RegistryImage.named(imageReference)
                                    .addCredentialRetriever(
                                            CredentialRetrieverFactory.forImage(
                                                    imageReference,
                                                    e -> getLogger().info(e.getMessage())
                                            ).dockerConfig()
                                    )
                    ))
            ).maxAttempt(3)
                    .exponentialBackoff(1000, 30000)
                    .onRetryError(e -> getLogger().warn("Error pushing jib image", e))
                    .execute();
        } catch (InvalidImageReferenceException e) {
            throw new GradleException("Invalid tag for local daemon import " + tag, e);
        }
    }

    private void maybeCreateLayerDirectories() {
        // If the task action ran this will be set to true, and we don't need need to repeat the opeeration
        // if the task is cached this won't be run and we do need to repeat the process
        if (!createdLayers) {
            createdLayers = true;
            if (getInstructions().stream().anyMatch(i -> i instanceof JibInstruction.Copy)) {
                super.copy();

                if (!getDidWork()) {
                    throw new GradleException(
                            "The configured copy specs didn't actually add any files to the image." +
                                    " Check the resulting layers int he build dir."
                    );
                }
            } else {
                setDidWork(true);
            }
        }
    }

    @Override
    @TaskAction
    protected void copy() {
        maybeCreateLayerDirectories();
        build(imageTag);
    }

    /*
     *
     * Utils
     *
     */


    public void saveDockerBuildInfo(DockerBuildInfo buildInfo) {
        try (FileWriter writer = new FileWriter(buildContext.imageBuildInfo().toFile())) {
            writer.write(new Gson().toJson(buildInfo));
        } catch (IOException e) {
            throw new GradleException("Error writing image info file", e);
        }
    }

    @Internal
    public DockerBuildContext getBuildContext() {
        return buildContext;
    }

    /*
     *
     * Dsl
     *
     */

    public void from(Project otherProject) {
        dependsOn((Callable) () -> otherProject.getTasks().named(DockerBasePlugin.BUILD_TASK_NAME));
        instructions.add(
                new JibInstruction.FromDockerBuildContext(
                        new DockerBuildContext(otherProject, DockerBasePlugin.BUILD_TASK_NAME)
                )
        );
    }

    public void maintainer(String name, String email) {
        instructions.add(
                new JibInstruction.Maintainer(name, email)
        );
    }

    public void copySpec(String owner, Action<CopySpec> copySpecAction) {
        final String layerName = "layer" + instructions.size();
        instructions.add(new JibInstruction.Copy(copySpecAction, DockerBuildContext.CONTEXT_FOLDER + "/" + layerName, owner));
        // This is an intersection point between Gradle and Docker so we need to instruct Gradle to create the layers
        // since Docker doesn't understand copySpecs.
        // This linkes the copy specs from the DSL together and is conceptually like adding an `into("layerX")`
        // to the orriginal copy spec
        with(
                getProject().copySpec(child -> {
                    child.into(layerName);
                    CopySpec dslSpec = getProject().copySpec();
                    copySpecAction.execute(dslSpec);
                    child.with(dslSpec);
                })
        );
    }

    public void copySpec(Action<CopySpec> copySpecAction) {
        copySpec(null, copySpecAction);
    }

    public void entryPoint(List<String> entrypoint) {
        instructions.add(new JibInstruction.Entrypoint(entrypoint));
    }

    public void cmd(List<String> cmd) {
        instructions.add(new JibInstruction.Cmd(cmd));
    }

    public void env(Pair<String, String> value) {
        instructions.add(new JibInstruction.Env(value.component1(), value.component2()));
    }

    public void exposeTcp(Integer port) {
        instructions.add(new JibInstruction.Expose(JibInstruction.Expose.Type.TCP, port));
    }

    public void exposeUdp(Integer port) {
        instructions.add(new JibInstruction.Expose(JibInstruction.Expose.Type.UDP, port));
    }

    public void label(String key, String value) {
        instructions.add(new JibInstruction.Label(key, value));
    }

    public void label(Pair<String, String> value) {
        instructions.add(new JibInstruction.Label(value.component1(), value.component2()));
    }

    public void changingLabel(String key, String value) {
        instructions.add(new JibInstruction.ChangingLabel(key, value));
    }

    public void changingLabel(Pair<String, String> value) {
        instructions.add(new JibInstruction.ChangingLabel(value.component1(), value.component2()));
    }

}
