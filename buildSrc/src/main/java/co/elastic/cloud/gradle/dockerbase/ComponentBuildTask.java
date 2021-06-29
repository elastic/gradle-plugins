package co.elastic.cloud.gradle.dockerbase;

import co.elastic.cloud.gradle.docker.DockerBuildContext;
import co.elastic.cloud.gradle.docker.DockerPluginConventions;
import co.elastic.cloud.gradle.docker.action.JibActions;
import co.elastic.cloud.gradle.docker.build.DockerBuildInfo;
import co.elastic.cloud.gradle.util.Architecture;
import co.elastic.cloud.gradle.util.OS;
import co.elastic.cloud.gradle.util.RetryUtils;
import com.google.cloud.tools.jib.api.*;
import com.google.cloud.tools.jib.frontend.CredentialRetrieverFactory;
import com.google.gson.Gson;
import kotlin.Pair;
import org.gradle.api.Action;
import org.gradle.api.GradleException;
import org.gradle.api.Project;
import org.gradle.api.tasks.*;

import javax.inject.Inject;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.util.*;

@CacheableTask
public class ComponentBuildTask extends Sync {

    private final DockerBuildContext buildContext;
    private boolean createdLayers = false;
    private Map<Architecture, List<JibInstruction>> instructionsPerPlatform = new HashMap<>();
    private DockerBuildContext fromContext;

    @Inject
    public ComponentBuildTask() {
        super();
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
    public Map<Architecture, List<JibInstruction>> getAllInstructions() {
        return instructionsPerPlatform;
    }

    /*
     *
     * Actions
     *
     */

    protected void build() {
        JibActions actions = new JibActions();

        DockerBuildInfo buildInfo = null;
        for (Map.Entry<Architecture, List<JibInstruction>> entry : instructionsPerPlatform.entrySet()) {
            final Architecture architecture = entry.getKey();
            String imageTag = DockerPluginConventions.componentImageTagWithPlatform(
                    getProject(),
                    architecture
            );
            if (fromContext == null) {
                throw new GradleException("Missing from context");
            }
            entry.getValue().add(
                new JibInstruction.From(DockerPluginConventions.baseImageTag(
                        fromContext.getProject(),
                        architecture
                ))
            );
            try {
                buildInfo = actions.buildTo(
                        buildContext,
                        entry.getValue(),
                        Containerizer.to(
                                TarImage.at(
                                        buildContext.projectTarImagePath(
                                                architecture
                                        )
                                ).named(imageTag))
                );
            } catch (InvalidImageReferenceException e) {
                throw new GradleException("Failed to build component image", e);
            }
        }
        saveDockerBuildInfo(buildInfo);
    }

    private void saveDockerBuildInfo(DockerBuildInfo buildInfo) {
        try (FileWriter writer = new FileWriter(buildContext.imageBuildInfo().toFile())) {
            writer.write(new Gson().toJson(buildInfo));
        } catch (IOException e) {
            throw new GradleException("Error writing image info file", e);
        }
    }


    protected void localImport(String tag) {
        maybeCreateLayerDirectories();
        try {
            ImageReference imageReference = ImageReference.parse(tag);
            JibActions actions = new JibActions();
            final List<JibInstruction> instructions = instructionsPerPlatform.get(Architecture.current());
            if (instructions == null) {
                throw new GradleException("Can't import image to local daemon, platform is unsupported");
            }
            instructions.add(
                new JibInstruction.FromDockerBuildContext(fromContext)
            );
            RetryUtils.retry(
                    () -> actions.buildTo(
                            buildContext,
                            instructions,
                            Containerizer.to(DockerDaemonImage.named(imageReference))
                    )
            )
                    .maxAttempt(3)
                    .exponentialBackoff(1000, 30000)
                    .onRetryError(e -> getLogger().warn("Error importing jib image", e))
                    .execute();
        } catch (InvalidImageReferenceException e) {
            throw new GradleException("Invalid tag for local daemon import " + tag, e);

        }
    }

    protected DockerBuildInfo push() {
        maybeCreateLayerDirectories();
        JibActions actions = new JibActions();
        DockerBuildInfo returnBuildInfo = null;
        for (Map.Entry<Architecture, List<JibInstruction>> entry : instructionsPerPlatform.entrySet()) {
            final Architecture architecture = entry.getKey();
            String imageTag = DockerPluginConventions.componentImageTagWithPlatform(
                    getProject(),
                    architecture
            );
            final ImageReference imageReference;
            try {
                imageReference = ImageReference.parse(imageTag);
            } catch (InvalidImageReferenceException e) {
                throw new GradleException("Failed to push component image", e);
            }

            entry.getValue().add(
                    new JibInstruction.From(DockerPluginConventions.baseImageTag(
                            fromContext.getProject(),
                            architecture
                    ))
            );
            final DockerBuildInfo buildInfo = RetryUtils.retry(() -> actions.buildTo(
                    buildContext,
                    entry.getValue(),
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
                    .onRetryError(e -> getLogger().warn("Error pushing jib image, will retry", e))
                    .execute();
            buildInfo.setTag(imageTag);
            saveDockerBuildInfo(buildInfo);
            getLogger().lifecycle("Pushed {} to registry", imageTag);
            if (architecture.equals(Architecture.X86_64)) {
                returnBuildInfo = buildInfo;
            }
        }
        return returnBuildInfo;
    }

    private void maybeCreateLayerDirectories() {
        // If the task action ran this will be set to true, and we don't need need to repeat the opeeration
        // if the task is cached this won't be run and we do need to repeat the process
        if (!createdLayers) {
            createdLayers = true;
            if (hasCopyInstructions()) {
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

    private boolean hasCopyInstructions() {
        return instructionsPerPlatform.entrySet().stream()
                .flatMap(each -> each.getValue().stream())
                .anyMatch(i -> i instanceof JibInstruction.Copy);
    }

    @Override
    @TaskAction
    protected void copy() {
        maybeCreateLayerDirectories();
        build();
    }

    @Internal
    public DockerBuildContext getBuildContext() {
        return buildContext;
    }

    public void images(List<Architecture> platformList, Action<ComponentBuildDSL> action) {
        for (Architecture entry : platformList) {
            final ComponentBuildDSL dsl = new ComponentBuildDSL(
                    this, entry
            );
            action.execute(dsl);

            final List<JibInstruction> platformInstructions = instructionsPerPlatform.getOrDefault(entry, new ArrayList<>());
            platformInstructions.addAll(dsl.instructions);
            instructionsPerPlatform.put(entry, platformInstructions);
        }
    }

    public void from(Project otherProject) {
        fromContext = new DockerBuildContext(otherProject, DockerBasePlugin.BUILD_TASK_NAME);
    }

    @Internal
    DockerBuildContext getFromContext() {
        return fromContext;
    }
}
