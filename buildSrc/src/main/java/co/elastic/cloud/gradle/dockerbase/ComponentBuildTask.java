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
import org.gradle.api.DefaultTask;
import org.gradle.api.GradleException;
import org.gradle.api.file.CopySpec;
import org.gradle.api.file.FileTree;
import org.gradle.api.provider.Provider;
import org.gradle.api.tasks.*;

import javax.inject.Inject;
import java.io.FileWriter;
import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

@CacheableTask
public class ComponentBuildTask extends DefaultTask {

    private final List<JibInstruction> instructions;
    private final DockerBuildContext buildContext;
    private final String imageTag;

    @Inject
    public ComponentBuildTask(String imageTag) {
        this.imageTag = imageTag;
        this.instructions = new ArrayList<>();
        this.buildContext = new DockerBuildContext(getProject(), getName());
    }

    /*
     *
     * Input tracking
     *
     */

    @OutputDirectory
    public Path getGeneratedImage() {
        return getBuildContext().jibApplicationLayerCachePath();
    }

    @OutputFile
    public Path getImageInfo() {
        return getBuildContext().imageBuildInfo();
    }

    @InputFiles
    @PathSensitive(PathSensitivity.RELATIVE)
    public FileTree getContext() {
        return getProject().fileTree(getBuildContext().contextPath());
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
            // Clean layers before building to avoid errors
            getProject().delete(buildContext.jibApplicationLayerCachePath());
            JibActions actions = new JibActions();
            ImageReference imageReference = ImageReference.parse(tag);
            DockerBuildInfo buildInfo = actions.buildTo(buildContext, instructions, Containerizer.to(TarImage.at(buildContext.projectTarImagePath()).named(tag)));
            saveDockerBuildInfo(buildInfo);
            return buildInfo;
        } catch (InvalidImageReferenceException e) {
            throw new GradleException("Invalid tag for local daemon import " + tag, e);

        }
    }

    protected void localImport(String tag) {
        try {
            ImageReference imageReference = ImageReference.parse(tag);
            JibActions actions = new JibActions();
            RetryUtils.retry(() -> actions.buildTo(buildContext, instructions, Containerizer.to(DockerDaemonImage.named(imageReference))))
                    .maxAttempt(3)
                    .exponentialBackoff(1000, 30000)
                    .onRetryError(e -> getLogger().warn("Error importing jib image", e))
                    .execute();
        } catch (InvalidImageReferenceException e) {
            throw new GradleException("Invalid tag for local daemon import " + tag, e);

        }
    }

    protected void push(String tag) {
        try {
            ImageReference imageReference = ImageReference.parse(tag);
            JibActions actions = new JibActions();
            RetryUtils.retry(() -> actions.buildTo(buildContext, instructions, Containerizer.to(RegistryImage.named(imageReference)
                    .addCredentialRetriever(
                            CredentialRetrieverFactory.forImage(
                                    imageReference,
                                    e -> getLogger().info(e.getMessage())
                            ).dockerConfig()
                    )))).maxAttempt(3)
                    .exponentialBackoff(1000, 30000)
                    .onRetryError(e -> getLogger().warn("Error pushing jib image", e))
                    .execute();
        } catch (InvalidImageReferenceException e) {
            throw new GradleException("Invalid tag for local daemon import " + tag, e);
        }
    }

    @TaskAction
    public void execute() {
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

    public void from(String image, String version, String sha) {
        instructions.add(new JibInstruction.From(image, version, sha));
    }

    public void from(Provider<BaseLocalImportTask> otherBuild) {
        dependsOn(otherBuild);
        instructions.add(new JibInstruction.FromDockerBuildContext(otherBuild));
    }

    public void copySpec(String owner, Action<CopySpec> copySpec) {
        instructions.add(new JibInstruction.Copy(copySpec, DockerBuildContext.CONTEXT_FOLDER + "/layer" + instructions.size(), owner));
    }

    public void copySpec(Action<CopySpec> copySpec) {
        copySpec(null, copySpec);
    }

    public void entryPoint(List<String> entrypoint) {
        instructions.add(new JibInstruction.Entrypoint(entrypoint));
    }

    public void cmd(List<String> cmd) {
        instructions.add(new JibInstruction.Cmd(cmd));
    }

    public void env(String key, String value) {
        instructions.add(new JibInstruction.Env(key, value));
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

    public static class ContextBuilder extends DefaultTask {
        private Provider<ComponentBuildTask> from;

        @Internal
        public Provider<ComponentBuildTask> getFrom() {
            return from;
        }

        public ContextBuilder setFrom(Provider<ComponentBuildTask> from) {
            this.from = from;
            return this;
        }

        @TaskAction
        public void execute() {
            if (from == null) {
                throw new GradleException(getName() + " task is not configured");
            }
            from.get().getInstructions().stream()
                    .filter(i -> i instanceof JibInstruction.Copy)
                    .map(i -> (JibInstruction.Copy) i)
                    .forEach(copy ->
                            getProject().copy(child -> {
                                child.into(from.get().getBuildContext().basePath().resolve(copy.getLayer()));
                                CopySpec dslSpec = getProject().copySpec();
                                copy.getSpec().execute(dslSpec);
                                child.with(dslSpec);
                            })
                    );
        }
    }
}
