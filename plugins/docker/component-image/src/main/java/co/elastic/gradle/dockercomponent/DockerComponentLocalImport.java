package co.elastic.gradle.dockercomponent;

import co.elastic.gradle.dockercomponent.lockfile.ComponentLockfile;
import co.elastic.gradle.utils.Architecture;
import co.elastic.gradle.utils.RegularFileUtils;
import co.elastic.gradle.utils.docker.ContainerImageProviderTask;
import co.elastic.gradle.utils.docker.DockerPluginConventions;
import co.elastic.gradle.utils.docker.DockerUtils;
import co.elastic.gradle.utils.docker.instruction.ChangingLabel;
import co.elastic.gradle.utils.docker.instruction.ContainerImageBuildInstruction;
import co.elastic.gradle.utils.docker.instruction.From;
import co.elastic.gradle.utils.docker.instruction.FromLocalArchive;
import org.gradle.api.DefaultTask;
import org.gradle.api.GradleException;
import org.gradle.api.file.FileSystemOperations;
import org.gradle.api.file.ProjectLayout;
import org.gradle.api.file.RegularFileProperty;
import org.gradle.api.internal.file.copy.DefaultCopySpec;
import org.gradle.api.provider.MapProperty;
import org.gradle.api.provider.Property;
import org.gradle.api.provider.Provider;
import org.gradle.api.provider.ProviderFactory;
import org.gradle.api.tasks.*;
import org.gradle.process.ExecOperations;

import javax.inject.Inject;
import java.io.File;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Collection;
import java.util.List;
import java.util.stream.Collectors;


abstract public class DockerComponentLocalImport extends DefaultTask implements ContainerImageProviderTask {

    final DefaultCopySpec rootCopySpec;

    public DockerComponentLocalImport() {
        getImageIdFile().convention(
                getProjectLayout()
                        .getBuildDirectory()
                        .file(getName() + "/" + "image-local.imageId")
                        .get()
        );

        rootCopySpec = getProject().getObjects().newInstance(DefaultCopySpec.class);
        rootCopySpec.addChildSpecListener(DockerPluginConventions.mapCopySpecToTaskInputs(this));
    }

    @OutputFile
    public File getMarker() {
        return new File(getProject().getBuildDir(), "docker_local_images/" + getName());
    }

    @Nested
    abstract MapProperty<Architecture, List<ContainerImageBuildInstruction>> getInstructions();

    @Input
    @Override
    public abstract Property<String> getTag();

    @Internal
    public Provider<String> getImageId() {
        //Convenience Provider to access the imageID from the imageIdFile
        return getImageIdFile().map(RegularFileUtils::readString).map(String::trim);
    }

    @OutputFile
    abstract public RegularFileProperty getImageIdFile();

    @Inject
    abstract protected ProjectLayout getProjectLayout();

    @Inject
    protected abstract ExecOperations getExecOperations();

    @Inject
    protected abstract FileSystemOperations getFilesystemOperations();

    @InputFiles
    @Optional
    @PathSensitive(PathSensitivity.RELATIVE)
    public abstract RegularFileProperty getLockFileLocation();

    @Inject
    protected abstract ProviderFactory getProviderFactory();

    @TaskAction
    public void localImport() throws IOException {
        final Path contextRoot = getProject().getBuildDir().toPath()
                .resolve(getName())
                .resolve("context");
        getFilesystemOperations().sync(spec -> {
                    spec.into(contextRoot);
                    spec.with(rootCopySpec);
                }
        );

        final JibActions actions = new JibActions();
        actions.buildToDaemon(
                getTag().get(),
                getImageIdFile().get(),
                getInstructions().get().get(Architecture.current())
                        .stream()
                        .filter(jibInstruction -> !(jibInstruction instanceof ChangingLabel))
                        .map(instruction -> {
                            if (isStaticFrom() && instruction instanceof From from) {
                                final Path lockfilePath = RegularFileUtils.toPath(getLockFileLocation());
                                if (!Files.exists(lockfilePath)) {
                                    throw new GradleException("A lockfile does not exist, run the `" +
                                                              DockerComponentPlugin.LOCK_FILE_TASK_NAME + "` task to generate it."
                                    );
                                }
                                final ComponentLockfile lockFile;
                                try {
                                    lockFile = ComponentLockfile.parse(
                                            Files.newBufferedReader(lockfilePath)
                                    );
                                } catch (IOException e) {
                                    throw new UncheckedIOException(e);
                                }
                                return actions.addDigestFromLockfile(
                                        lockFile.images().get(Architecture.current()), from, getProviderFactory()
                                );
                            } else {
                                return instruction;
                            }
                        })
                        .collect(Collectors.toList()),
                contextRoot
        );
        Files.writeString(getMarker().toPath(), getTag().get());
        getLogger().lifecycle("Image with Id {} tagged as {}",
                RegularFileUtils.readString(getImageIdFile().get()).trim(),
                getTag().get()
        );
        if (getLogger().isInfoEnabled()) {
            (new DockerUtils(getExecOperations())).exec(execSpec -> {
                execSpec.commandLine("docker", "inspect", getTag().get());
            });
        }
    }

    private boolean isStaticFrom() {
        return ! getInstructions().get().values().stream()
                .flatMap(Collection::stream)
                .anyMatch(each -> each instanceof FromLocalArchive);
    }
}
