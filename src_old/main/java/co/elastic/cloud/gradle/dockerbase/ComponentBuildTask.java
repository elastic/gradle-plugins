package co.elastic.cloud.gradle.dockerbase;

import co.elastic.cloud.gradle.docker.DockerPluginConventions;
import co.elastic.cloud.gradle.docker.action.JibActions;
import co.elastic.cloud.gradle.util.Architecture;
import co.elastic.cloud.gradle.util.FileUtils;
import org.gradle.api.Action;
import org.gradle.api.DefaultTask;
import org.gradle.api.file.ProjectLayout;
import org.gradle.api.file.RegularFile;
import org.gradle.api.internal.file.copy.*;
import org.gradle.api.provider.MapProperty;
import org.gradle.api.provider.Provider;
import org.gradle.api.tasks.*;

import javax.inject.Inject;
import java.io.File;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.util.*;
import java.util.Collection;
import java.util.function.Function;
import java.util.stream.Collectors;

@CacheableTask
abstract public class ComponentBuildTask extends DefaultTask {

    public static final String LAYERS_DIR = "context";
    private final Map<Architecture, List<JibInstruction>> instructionsPerPlatform;
    private final List<Action<ComponentBuildDSL>> configForAllArchitectures;
    final DefaultCopySpec rootCopySpec;

    @Inject
    public ComponentBuildTask(CopySpecInternal.CopySpecListener importListener) {
        super();

        // These need to be a convention so they are set even if task actions don't execute (task is cached)
        getImageArchive().convention(
                getInstructions().map(map ->
                        map.keySet().stream()
                                .collect(Collectors.toMap(
                                        Function.identity(),
                                        architecture -> getProjectLayout()
                                                .getBuildDirectory()
                                                .file(getName() + "/" + "image-" + architecture + ".tar.zstd")
                                                .get()
                                ))
                )
        );

        getImageIdFile().convention(
                getInstructions().map(map ->
                        map.keySet().stream()
                                .collect(Collectors.toMap(
                                        Function.identity(),
                                        architecture -> getProjectLayout()
                                                .getBuildDirectory()
                                                .file(getName() + "/" + "image-" + architecture + ".imageId")
                                                .get())
                                )
                )
        );

        getCreatedAtFile().convention(
                getInstructions().map(map ->
                        map.keySet().stream()
                                .collect(Collectors.toMap(
                                        Function.identity(),
                                        architecture -> getProjectLayout()
                                                .getBuildDirectory()
                                                .file(getName() + "/" + "image-" + architecture + ".createdAt")
                                                .get())
                                )
                )
        );

        instructionsPerPlatform = new HashMap<>();
        getInstructions().convention(instructionsPerPlatform);

        configForAllArchitectures = new ArrayList<>();

        rootCopySpec = getProject().getObjects().newInstance(DefaultCopySpec.class);
        rootCopySpec.addChildSpecListener(DockerPluginConventions.mapCopySpecToTaskInputs(this));
        rootCopySpec.addChildSpecListener(importListener);
    }

    @OutputFiles
    abstract MapProperty<Architecture, RegularFile> getImageArchive();

    @OutputFiles
    abstract MapProperty<Architecture, RegularFile> getImageIdFile();

    @OutputFiles
    abstract MapProperty<Architecture, RegularFile> getCreatedAtFile();


    @Nested
    abstract MapProperty<Architecture, List<JibInstruction>> getInstructions();

    @Input
    public List<String> getBaseImageIds() {
        final JibActions jibActions = new JibActions();
        return getInstructions().get().entrySet().stream()
                .map(Map.Entry::getValue)
                .flatMap(Collection::stream)
                .filter((instruction) -> instruction instanceof JibInstruction.From)
                .map((it) -> (JibInstruction.From) it)
                .map(from -> jibActions.getImageId(from.getReference()))
                .sorted() // Make sure the order doesn't invalidate the cache
                .collect(Collectors.toList());
    }

    @Inject
    abstract protected ProjectLayout getProjectLayout();

    @Internal
    public Provider<Map<Architecture, String>> getImageId() {
        return getImageIdFile().map(idFiles -> idFiles.entrySet()
                .stream()
                // We only build the full set of architectures in CI, so won't have all IDs on every run
                .filter(entry -> entry.getValue().getAsFile().exists())
                .collect(Collectors.toMap(Map.Entry::getKey, entry -> {
                    return FileUtils.readFromRegularFile(entry.getValue());
                })));
    }

    /*
     *
     * Actions
     *
     */

    @TaskAction
    protected void buildComponentImages() throws IOException {
        createLayersDir();
        JibActions actions = new JibActions();

        for (Map.Entry<Architecture, List<JibInstruction>> entry : getInstructions().get().entrySet()) {
            final Architecture architecture = entry.getKey();
            actions.buildTo(
                    getImageArchive().get().get(architecture),
                    getImageIdFile().get().get(architecture),
                    getCreatedAtFile().get().get(architecture),
                    entry.getValue()
            );
        }

        GradleCacheUtilities.assertOutputSize(
                getPath(),
                getImageArchive().get().values().stream()
                        .map(RegularFile::getAsFile)
                        .map(File::toPath)
                        .filter(Files::exists) // Not all of them will exist locally
                        .map(path -> {
                            try {
                                return Files.size(path);
                            } catch (IOException e) {
                                throw new UncheckedIOException(e);
                            }
                        })
                        .reduce(0l, Long::sum)

        );
    }

    public void createLayersDir() {
        getProject().sync(spec -> {
                    spec.into(getProjectLayout().getBuildDirectory().file(getName() + "/" + LAYERS_DIR).get().getAsFile());
                    spec.with(rootCopySpec);
                }
        );
    }

    @Internal
    public DefaultCopySpec getRootCopySpec() {
        return rootCopySpec;
    }

    public void buildOnly(List<Architecture> platformList, Action<ComponentBuildDSL> action) {
        for (Architecture architecture : platformList) {
            // Add configuration that was already registered for this architecture
            for (Action<ComponentBuildDSL> configForAllArchitecture : configForAllArchitectures) {
                addInstructionsPerArchitecture(configForAllArchitecture, architecture);
            }
            // Add the current configuration
            addInstructionsPerArchitecture(action, architecture);
        }
    }

    public void configure(Action<ComponentBuildDSL> action) {
        // Store this configuration as we'll need it in case we add an architecture lather
        configForAllArchitectures.add(action);
        // Add the additional configuration for already configured architectures
        for (Architecture architecture : instructionsPerPlatform.keySet()) {
            addInstructionsPerArchitecture(action, architecture);
        }
    }

    public void buildAll(Action<ComponentBuildDSL> action) {
        buildOnly(Arrays.asList(Architecture.values()), action);
    }

    private void addInstructionsPerArchitecture(Action<ComponentBuildDSL> action, Architecture architecture) {
        final ComponentBuildDSL dsl = new ComponentBuildDSL(this, architecture);
        action.execute(dsl);

        final List<JibInstruction> platformInstructions = instructionsPerPlatform.getOrDefault(architecture, new ArrayList<>());
        platformInstructions.addAll(dsl.instructions);
        instructionsPerPlatform.put(architecture, platformInstructions);
    }
}
