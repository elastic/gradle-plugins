package co.elastic.gradle.dockercomponent;

import co.elastic.gradle.dockercomponent.lockfile.ComponentLockfile;
import co.elastic.gradle.utils.Architecture;
import co.elastic.gradle.utils.RegularFileUtils;
import co.elastic.gradle.utils.docker.DockerPluginConventions;
import co.elastic.gradle.utils.docker.GradleCacheUtilities;
import co.elastic.gradle.utils.docker.instruction.ContainerImageBuildInstruction;
import co.elastic.gradle.utils.docker.instruction.From;
import co.elastic.gradle.utils.docker.instruction.FromLocalArchive;
import org.gradle.api.DefaultTask;
import org.gradle.api.GradleException;
import org.gradle.api.file.ProjectLayout;
import org.gradle.api.file.RegularFile;
import org.gradle.api.file.RegularFileProperty;
import org.gradle.api.internal.file.copy.DefaultCopySpec;
import org.gradle.api.provider.MapProperty;
import org.gradle.api.provider.Property;
import org.gradle.api.provider.Provider;
import org.gradle.api.provider.ProviderFactory;
import org.gradle.api.tasks.*;

import javax.inject.Inject;
import java.io.File;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

@CacheableTask
abstract public class ComponentBuildTask extends DefaultTask {

    public static final String LAYERS_DIR = "context";

    final DefaultCopySpec rootCopySpec;

    @Inject
    public ComponentBuildTask() {
        super();
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

        rootCopySpec = getProject().getObjects().newInstance(DefaultCopySpec.class);
        rootCopySpec.addChildSpecListener(DockerPluginConventions.mapCopySpecToTaskInputs(this));
    }

    @OutputFiles
    abstract MapProperty<Architecture, RegularFile> getImageArchive();

    @OutputFiles
    abstract MapProperty<Architecture, RegularFile> getImageIdFile();

    @OutputFiles
    abstract MapProperty<Architecture, RegularFile> getCreatedAtFile();

    @Nested
    public abstract MapProperty<Architecture, List<ContainerImageBuildInstruction>> getInstructions();

    @Input
    public List<String> getBaseImageIds() {
        final JibActions jibActions = new JibActions();

        if (!isStaticFrom()) {
            // In case we have a FromLocalArchive instruction, we are building from dynamically pushed base images,
            // so no lockfile and no digest and the tag on its own doesn't guarantee correct build avoidance as the
            // image it points to could be changing, e.g. in the case of a hard coded project.version
            return getInstructions().get().values().stream()
                    .flatMap(Collection::stream)
                    .filter((instruction) -> instruction instanceof From)
                    .map((it) -> (From) it)
                    .map(from -> jibActions.getImageId(from.getReference().get()))
                    .sorted() // Make sure the order doesn't invalidate the cache
                    .collect(Collectors.toList());
        } else {
            return Collections.emptyList();
        }
    }

    private boolean isStaticFrom() {
        return ! getInstructions().get().values().stream()
                .flatMap(Collection::stream)
                .anyMatch(each -> each instanceof FromLocalArchive);
    }

    @InputFiles
    @Optional
    @PathSensitive(PathSensitivity.RELATIVE)
    public abstract RegularFileProperty getLockFileLocation();

    @Inject
    abstract protected ProjectLayout getProjectLayout();

    @Inject
    protected abstract JibActions getJibActions();

    @Internal
    public Provider<Map<Architecture, String>> getImageId() {
        return getImageIdFile().map(idFiles -> idFiles.entrySet()
                .stream()
                // We only build the full set of architectures in CI, so won't have all IDs on every run
                .filter(entry -> entry.getValue().getAsFile().exists())
                .collect(Collectors.toMap(
                        Map.Entry::getKey,
                        entry -> RegularFileUtils.readString(entry.getValue()).trim())
                )
        );
    }

    @Input
    public abstract Property<Long> getMaxOutputSizeMB();

    @Inject
    protected abstract ProviderFactory getProviderFactory();

    @TaskAction
    protected void buildComponentImages() throws IOException {
        getProject().sync(spec -> {
                    spec.into(getProjectLayout().getBuildDirectory().file(getName() + "/" + LAYERS_DIR).get().getAsFile());
                    spec.with(rootCopySpec);
                }
        );
        JibActions actions = new JibActions();

        final ComponentLockfile lockFile;
        if (isStaticFrom()) {
            final Path lockfilePath = RegularFileUtils.toPath(getLockFileLocation());
            if (!Files.exists(lockfilePath)) {
                throw new GradleException("A lockfile does not exist, run the `" +
                                          DockerComponentPlugin.LOCK_FILE_TASK_NAME + "` task to generate it."
                );
            }
            lockFile = ComponentLockfile.parse(
                    Files.newBufferedReader(lockfilePath)
            );
        } else {
            lockFile = null;
        }

        for (Map.Entry<Architecture, List<ContainerImageBuildInstruction>> entry : getInstructions().get().entrySet()) {
            final Architecture architecture = entry.getKey();
            actions.buildArchive(
                    entry.getKey(),
                    getImageArchive().get().get(architecture),
                    getImageIdFile().get().get(architecture),
                    getCreatedAtFile().get().get(architecture),
                    entry.getValue().stream()
                            .map(instruction -> {
                                if (instruction instanceof From from) {
                                    if (lockFile != null) {
                                        return actions.addDigestFromLockfile(
                                                lockFile.images().get(entry.getKey()), from, getProviderFactory()
                                        );
                                    } else {
                                        return instruction;
                                    }
                                } else {
                                    return instruction;
                                }
                            })
                            .toList()
            );
        }

        if (getMaxOutputSizeMB().get() > 0) {
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
                            .reduce(0L, Long::sum),
                    getMaxOutputSizeMB().get()
            );
        }
    }

}
