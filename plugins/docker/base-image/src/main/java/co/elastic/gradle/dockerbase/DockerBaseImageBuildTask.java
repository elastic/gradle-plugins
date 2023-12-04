package co.elastic.gradle.dockerbase;

import co.elastic.gradle.dockerbase.lockfile.BaseLockfile;
import co.elastic.gradle.dockerbase.lockfile.Packages;
import co.elastic.gradle.utils.Architecture;
import co.elastic.gradle.utils.RegularFileUtils;
import co.elastic.gradle.utils.docker.DockerPluginConventions;
import co.elastic.gradle.utils.docker.DockerUtils;
import co.elastic.gradle.utils.docker.GradleCacheUtilities;
import co.elastic.gradle.utils.docker.UnchangingContainerReference;
import co.elastic.gradle.utils.docker.instruction.ContainerImageBuildInstruction;
import co.elastic.gradle.utils.docker.instruction.From;
import co.elastic.gradle.utils.docker.instruction.Install;
import org.apache.commons.compress.compressors.zstandard.ZstdCompressorOutputStream;
import org.gradle.api.DefaultTask;
import org.gradle.api.GradleException;
import org.gradle.api.file.DirectoryProperty;
import org.gradle.api.file.ProjectLayout;
import org.gradle.api.file.RegularFileProperty;
import org.gradle.api.internal.file.copy.DefaultCopySpec;
import org.gradle.api.model.ObjectFactory;
import org.gradle.api.provider.ListProperty;
import org.gradle.api.provider.Property;
import org.gradle.api.provider.Provider;
import org.gradle.api.provider.ProviderFactory;
import org.gradle.api.tasks.*;
import org.gradle.process.ExecOperations;
import org.gradle.process.ExecResult;
import org.jetbrains.annotations.NotNull;

import javax.inject.Inject;
import java.io.BufferedOutputStream;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;
import java.util.stream.Stream;

@CacheableTask
public abstract class DockerBaseImageBuildTask extends DefaultTask implements ImageBuildable {

    private final DefaultCopySpec rootCopySpec;

    @Inject
    public DockerBaseImageBuildTask() {
        super();

        final String baseFileName = getName() + "/" + "image-" + Architecture.current().name().toLowerCase();

        getImageArchive().convention(
                getProjectLayout().getBuildDirectory().file(baseFileName + ".tar.zstd")
        );
        getWorkingDirectory().convention(
                getProjectLayout().getBuildDirectory().dir(getName())
        );
        getImageIdFile().convention(
                getProjectLayout().getBuildDirectory().file(baseFileName + ".idfile")
        );
        getCreatedAtFile().convention(
                getProjectLayout().getBuildDirectory().file(baseFileName + ".createdAt")
        );
        // TODO: Change to true once packages are archived properly
        getOnlyUseMirrorRepositories().convention(false);
        getRequiresCleanLayers().convention(true);

        rootCopySpec = getProject().getObjects().newInstance(DefaultCopySpec.class);
        rootCopySpec.addChildSpecListener(DockerPluginConventions.mapCopySpecToTaskInputs(this));
    }


    @Input
    @NotNull
    public abstract Property<OSDistribution> getOSDistribution();

    @OutputFile
    public abstract RegularFileProperty getImageIdFile();

    @Nested
    public abstract Property<BaseLockfile> getLockFile();

    @Nested
    public abstract ListProperty<OsPackageRepository> getMirrorRepositories();

    @Nested
    public abstract ListProperty<ContainerImageBuildInstruction> getInputInstructions();

    @Nested
    public List<ContainerImageBuildInstruction> getActualInstructions() {
        BaseLockfile lockfile = getLockFile().get();
        List<ContainerImageBuildInstruction> instructions = getInputInstructions().get();

        Packages packages = lockfile.getPackages().get(getArchitecture());

        if (lockfile.getImage() == null) {
            if (instructions.stream()
                    .anyMatch(each -> each instanceof From)) {
                throw new GradleException("Missing image in lockfile. Does the lockfile need to be regenerated?");
            }
        }

        final Set<String> allPackages = instructions.stream()
                .filter(each -> each instanceof Install)
                .map(each -> (Install) each)
                .flatMap(each -> each.getPackages().stream())
                .collect(Collectors.toSet());

        return Stream.concat(
                instructions.stream()
                        .map(instruction -> {
                            if (instruction instanceof From from) {
                                if (lockfile.getImage() == null) {
                                    throw new GradleException("Missing image in lockfile, does it need to be regenerated?");
                                }
                                UnchangingContainerReference lockedImage = lockfile.getImage().get(Architecture.current());
                                if (from.getReference().get().contains("@")) {
                                    throw new IllegalStateException(
                                            "The sha should come from the lockfile and thus should " +
                                            "not be specified in the input instructions."
                                    );
                                }
                                if (!from.getReference().get().contains(lockedImage.getRepository()) ||
                                    !from.getReference().get().contains(lockedImage.getTag())
                                ) {
                                    throw new GradleException(
                                            "Can't find " + from.getReference().get() + " in the lockfile. " +
                                            "Does the lockfile needs to be regenerated?\n" + lockedImage.getTag()
                                    );
                                }
                                return new From(
                                        getProviderFactory().provider(() -> String.format(
                                                "%s:%s@%s",
                                                lockedImage.getRepository(),
                                                lockedImage.getTag(),
                                                lockedImage.getDigest()
                                        ))
                                );
                            } else if (instruction instanceof Install install) {
                                final List<String> missingPackages = install.getPackages().stream()
                                        .filter(each -> packages.findByName(each).isEmpty())
                                        .toList();
                                if (!missingPackages.isEmpty()) {
                                    throw new GradleException(
                                            "Does the lockfile need to be regenerated? The following packages are missing from the lockfile:\n" +
                                            String.join(",", missingPackages)
                                    );
                                }
                                final OSDistribution distribution = getOSDistribution().get();
                                return new Install(
                                        install.getPackages().stream()
                                                .map(packages::findByName)
                                                .map(Optional::get)
                                                .map(each -> each.getPackageName(distribution))
                                                .toList()
                                );
                            } else {
                                return instruction;
                            }
                        }),
                Stream.of(
                        // Add an installation instruction for packages in the lockfile but not the DSL.
                        // These are the implicit packages from the base image, but we want to be sure they are at the
                        // same version as specified in the lockfile.
                        new Install(
                                packages.getPackages().stream()
                                        .filter(each -> !allPackages.contains(each.getName()))
                                        .map(each -> each.getPackageName(getOSDistribution().get()))
                                        .toList()
                        )
                )
        ).toList();
    }


    @Inject
    protected abstract ProviderFactory getProviderFactory();

    @Override
    @Internal
    public DefaultCopySpec getRootCopySpec() {
        return rootCopySpec;
    }

    @Inject
    abstract protected ExecOperations getExecOperations();

    @Inject
    abstract protected ProjectLayout getProjectLayout();

    @Inject
    abstract protected ObjectFactory getObjectFactory();

    @Override
    @Internal
    public Provider<String> getImageId() {
        //Convenience Provider to access the imageID from the imageIdFile
        return getImageIdFile().map(RegularFileUtils::readString).map(String::trim);
    }

    @OutputFile
    public abstract RegularFileProperty getCreatedAtFile();

    @OutputFile
    public abstract RegularFileProperty getImageArchive();

    @Internal
    public Provider<Instant> getCreatedAt() {
        //Convenience Provider to access the creation date  from the createdAt file
        return getCreatedAtFile()
                .map(RegularFileUtils::readString)
                .map(String::trim)
                .map(Instant::parse);
    }

    @Input
    public Architecture getArchitecture() {
        return Architecture.current();
    }

    @Input
    public abstract Property<Long> getMaxOutputSizeMB();

    @LocalState
    @Override
    public abstract DirectoryProperty getWorkingDirectory();

    @Override
    @Input
    public abstract Property<String> getDockerEphemeralMount();

    @Override
    @Input
    public abstract Property<Boolean> getRequiresCleanLayers();

    @Override
    @Input
    public abstract Property<Boolean> getOnlyUseMirrorRepositories();

    private void buildDockerImage() {
        DockerDaemonActions daemonActions = getObjectFactory().newInstance(DockerDaemonActions.class, this);
        DockerUtils dockerUtils = new DockerUtils(getExecOperations());
        try {
            UUID uuid = daemonActions.build();

            try (BufferedOutputStream createAtFileOut = new BufferedOutputStream(
                    Files.newOutputStream(RegularFileUtils.toPath(getCreatedAtFile())))
            ) {
                int imageInspect = dockerUtils.exec(spec -> {
                    spec.setWorkingDir(getWorkingDirectory());
                    spec.setStandardOutput(createAtFileOut);
                    spec.commandLine("docker", "image", "inspect", "--format", "{{.Created}}", uuid);
                    spec.setIgnoreExitValue(true);
                }).getExitValue();
                if (imageInspect != 0) {
                    throw new GradleException(
                            "Failed to inspect docker image, see the docker build log in the task output"
                    );
                }
            }

            final Path imageArchive = RegularFileUtils.toPath(getImageArchive());
            try (ZstdCompressorOutputStream compressedOut = new ZstdCompressorOutputStream(
                    new BufferedOutputStream(Files.newOutputStream(imageArchive)))) {
                ExecResult imageSave = dockerUtils.exec(spec -> {
                    spec.setStandardOutput(compressedOut);
                    spec.setCommandLine("docker", "save", uuid.toString());
                    spec.setIgnoreExitValue(true);
                });
                if (imageSave.getExitValue() != 0) {
                    throw new GradleException(
                            "Failed to save docker image, see the docker build log in the task output"
                    );
                }
            }

            dockerUtils.exec(spec -> {
                spec.commandLine("docker", "image", "rm", "-f", uuid);
                spec.setIgnoreExitValue(false);
            });
        } catch (IOException e) {
            throw new GradleException("Error building docker base image", e);
        }
    }

    @TaskAction
    protected void taskAction() {
        buildDockerImage();
        final Long maxSizeMB = getMaxOutputSizeMB().get();
        if (maxSizeMB > 0) {
            try {
                GradleCacheUtilities.assertOutputSize(
                        getPath(),
                        Files.size(RegularFileUtils.toPath(getImageArchive())),
                        maxSizeMB
                );
            } catch (IOException e) {
                throw new UncheckedIOException(e);
            }
        }
    }


}
