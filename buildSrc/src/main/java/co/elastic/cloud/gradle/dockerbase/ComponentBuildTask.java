package co.elastic.cloud.gradle.dockerbase;

import co.elastic.cloud.gradle.docker.action.JibActions;
import co.elastic.cloud.gradle.util.Architecture;
import co.elastic.cloud.gradle.util.GradleUtils;
import org.gradle.api.Action;
import org.gradle.api.GradleException;
import org.gradle.api.file.CopySpec;
import org.gradle.api.file.ProjectLayout;
import org.gradle.api.file.RegularFile;
import org.gradle.api.provider.MapProperty;
import org.gradle.api.provider.Provider;
import org.gradle.api.tasks.*;

import javax.inject.Inject;
import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.util.*;
import java.util.concurrent.Callable;
import java.util.function.Function;
import java.util.stream.Collectors;

@CacheableTask
abstract public class ComponentBuildTask extends Sync {

    public static final String LAYERS_DIR = "context";

    @Inject
    public ComponentBuildTask() {
        super();

        // These need to be a convention so they are set even if task actions don't execute (task is cached)
        getImageArchive().convention(
                getInstructions().map(map ->
                        map.keySet().stream()
                                .collect(Collectors.toMap(
                                        Function.identity(),
                                        architecture -> getProjectLayout()
                                                .getBuildDirectory()
                                                .file(getName() + "/" + "image-" + architecture + ".tar")
                                                .get())
                                )
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

        setDestinationDir(getProjectLayout().getBuildDirectory().file(getName() + "/" + LAYERS_DIR).get().getAsFile());

        // Without this, the task will be skipped if there's nothing to add to the image
        final CopySpec spec = this.getRootSpec().addChild();
        spec.from((Callable<File>) () -> {
            final File file = new File(getProject().getBuildDir(), ".elastic");
            Files.createDirectories(file.getParentFile().toPath());
            Files.write(file.toPath(), "".getBytes());
            return file;
        });
    }

    @OutputFiles
    abstract MapProperty<Architecture, RegularFile> getImageArchive();

    @OutputFiles
    abstract MapProperty<Architecture, RegularFile> getImageIdFile();

    @Nested
    abstract MapProperty<Architecture, List<JibInstruction>> getInstructions();

    @Inject
    abstract protected ProjectLayout getProjectLayout();

    @Internal
    public Provider<Map<Architecture, String>> getImageId() {
        return getImageIdFile().map(idFiles -> idFiles.entrySet()
                .stream()
                // We only build the full set of architectures in CI, so won't have all IDs on every run
                .filter(entry -> entry.getValue().getAsFile().exists())
                .collect(Collectors.toMap(Map.Entry::getKey, entry -> {
                    try {
                        return Files.readAllLines(entry.getValue().getAsFile().toPath()).get(0);
                    } catch (IOException e) {
                        throw new GradleException("Internal error: Can't read Iamge ID file: " + entry.getValue(), e);
                    }
                })));
    }

    /*
     *
     * Actions
     *
     */

    protected void build() {
        JibActions actions = new JibActions();

        for (Map.Entry<Architecture, List<JibInstruction>> entry : getInstructions().get().entrySet()) {
            final Architecture architecture = entry.getKey();
            if (!GradleUtils.isCi() && !architecture.equals(Architecture.current())) {
                // Skip building other architectures unless we are running in CI as the base images might not have been
                // pushed
                return;
            }
            actions.buildTo(
                    getImageArchive().get().get(architecture),
                    getImageIdFile().get().get(architecture),
                    entry.getValue()
            );
        }
    }

    private void maybeCreateLayerDirectories() {
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

    private boolean hasCopyInstructions() {
        return getInstructions().get().entrySet().stream()
                .flatMap(each -> each.getValue().stream())
                .anyMatch(i -> i instanceof JibInstruction.Copy);
    }

    @Override
    @TaskAction
    protected void copy() {
        maybeCreateLayerDirectories();
        build();
    }

    /**
     * Configures the architectures we consider and the image content for each
     *
     * @param platformList List of platforms to build the image for
     * @param action Action to configure the images
     */
    public void images(List<Architecture> platformList, Action<ComponentBuildDSL> action) {
        final Map<Architecture, List<JibInstruction>> instructionsPerPlatform = new HashMap<>();
        for (Architecture entry : platformList) {
            final ComponentBuildDSL dsl = new ComponentBuildDSL(this, entry);
            action.execute(dsl);

            final List<JibInstruction> platformInstructions = instructionsPerPlatform.getOrDefault(entry, new ArrayList<>());
            platformInstructions.addAll(dsl.instructions);
            instructionsPerPlatform.put(entry, platformInstructions);
        }
        getInstructions().convention(instructionsPerPlatform);
    }
}
