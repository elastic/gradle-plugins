package co.elastic.gradle.dockerbase;


import co.elastic.gradle.dockerbase.lockfile.Lockfile;
import co.elastic.gradle.dockerbase.lockfile.Packages;
import co.elastic.gradle.dockerbase.lockfile.UnchangingContainerReference;
import co.elastic.gradle.dockerbase.lockfile.UnchangingPackage;
import co.elastic.gradle.utils.Architecture;
import co.elastic.gradle.utils.RegularFileUtils;
import co.elastic.gradle.utils.docker.DockerPluginConventions;
import co.elastic.gradle.utils.docker.DockerUtils;
import co.elastic.gradle.utils.docker.instruction.ContainerImageBuildInstruction;
import co.elastic.gradle.utils.docker.instruction.From;
import co.elastic.gradle.utils.docker.instruction.SetUser;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.apache.commons.csv.CSVFormat;
import org.apache.commons.csv.CSVParser;
import org.gradle.api.DefaultTask;
import org.gradle.api.GradleException;
import org.gradle.api.artifacts.Configuration;
import org.gradle.api.file.DirectoryProperty;
import org.gradle.api.file.ProjectLayout;
import org.gradle.api.file.RegularFileProperty;
import org.gradle.api.internal.file.copy.DefaultCopySpec;
import org.gradle.api.model.ObjectFactory;
import org.gradle.api.provider.ListProperty;
import org.gradle.api.provider.Property;
import org.gradle.api.tasks.*;
import org.gradle.process.ExecOperations;

import javax.inject.Inject;
import java.io.*;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.attribute.PosixFilePermission;
import java.time.Instant;
import java.util.Optional;
import java.util.*;
import java.util.stream.Stream;

public abstract class DockerLockfileTask extends DefaultTask implements ImageBuildable {

    private static final String PRINT_INSTALLED_PACKAGES_NAME = "print-installed-packages.sh";
    private final Configuration dockerEphemeralConfiguration;
    private final DefaultCopySpec rootCopySpec;
    private String manifestDigest = null;

    @Inject
    public DockerLockfileTask(Configuration dockerEphemeralConfiguration) {
        getImageIdFile().convention(
                getProjectLayout().getBuildDirectory().file(getName() + "/tmp.image.id")
        );
        getWorkingDirectory().convention(
                getProjectLayout().getBuildDirectory().dir(getName())
        );
        this.dockerEphemeralConfiguration = dockerEphemeralConfiguration;
        rootCopySpec = getProject().getObjects().newInstance(DefaultCopySpec.class);
        rootCopySpec.addChildSpecListener(DockerPluginConventions.mapCopySpecToTaskInputs(this));
    }

    @Input
    public Instant getCurrentTime() {
        // Task should never be considered up-to-date
        return Instant.now();
    }

    @Override
    @OutputFile
    public abstract RegularFileProperty getImageIdFile();

    @Override
    @LocalState
    public abstract DirectoryProperty getWorkingDirectory();

    @Override
    @Input
    public abstract Property<OSDistribution> getOSDistribution();

    @Override
    @Nested
    public abstract ListProperty<OsPackageRepository> getMirrorRepositories();

    @Override
    @InputFiles
    public Configuration getDockerEphemeralConfiguration() {
        return dockerEphemeralConfiguration;
    }

    @Override
    @Internal
    public DefaultCopySpec getRootCopySpec() {
        return rootCopySpec;
    }

    @Override
    @Input
    public abstract Property<String> getDockerEphemeralMount();

    @Nested
    public abstract ListProperty<ContainerImageBuildInstruction> getInputInstructions();

    @Override
    @Nested
    public List<ContainerImageBuildInstruction> getActualInstructions() {
        // Use the last available digest for this image
        return Stream.concat(
                        getInputInstructions().get().stream()
                                .map(instruction -> {
                                    if (instruction instanceof From from) {
                                        if (from.getSha() != null) {
                                            throw new IllegalStateException("Input instruction can't have a digest");
                                        }
                                        return new From(
                                                from.getImage(),
                                                from.getVersion(),
                                                getManifestDigest(from.getReference())
                                        );
                                    } else {
                                        return instruction;
                                    }
                                }),
                        Stream.of(
                                new SetUser("root"),
                                DockerDaemonActions.wrapInstallCommand(
                                        getOSDistribution().get(),
                                        switch (getOSDistribution().get()) {
                                            case UBUNTU, DEBIAN -> "apt-get -y --allow-unauthenticated upgrade";
                                            case CENTOS -> "yum -y upgrade";
                                        }
                                )
                        )
                )
                .toList();
    }

    @Internal
    public abstract RegularFileProperty getLockFileLocation();

    @Inject
    protected abstract ProjectLayout getProjectLayout();

    @Inject
    protected abstract ExecOperations getExecOperations();

    @Inject
    protected abstract ObjectFactory getObjectFactory();

    @TaskAction
    public void generateLockfile() throws IOException {
        DockerUtils dockerUtils = new DockerUtils(getExecOperations());
        DockerDaemonActions daemonActions = getObjectFactory().newInstance(DockerDaemonActions.class, this);
        final UUID uuid = daemonActions.build();

        final Path csvGenScript = writeScript(RegularFileUtils.toPath(getWorkingDirectory()));

        final ByteArrayOutputStream byteArrayOutputStream = new ByteArrayOutputStream();
        dockerUtils.exec(spec -> {
            spec.setStandardOutput(byteArrayOutputStream);
            spec.commandLine(
                    "docker", "run", "--rm",
                    "-v", csvGenScript + ":/mnt/script.sh",
                    "--entrypoint", "/bin/bash",
                    uuid,
                    "/mnt/script.sh"
            );
            spec.setIgnoreExitValue(false);
        });

        dockerUtils.exec(spec -> spec.commandLine("docker", "image", "rm", uuid));

        final Lockfile oldLockfile;
        if (Files.exists(RegularFileUtils.toPath(getLockFileLocation()))) {
            oldLockfile = Lockfile.parse(Files.newBufferedReader(RegularFileUtils.toPath(getLockFileLocation())));
        } else {
            oldLockfile = new Lockfile(Map.of(), null);
        }
        final Map<Architecture, Packages> packages = new HashMap<>(oldLockfile.getPackages());
        Map<Architecture, UnchangingContainerReference> image;
        if (oldLockfile.getImage() != null) {
            image = new HashMap<>(oldLockfile.getImage());
        } else {
            image = null;
        }

        final String csvString = byteArrayOutputStream.toString().trim();
        if (csvString.isEmpty()) {
            throw new IllegalStateException("Failed to read installed packages from docker image");
        }
        try (Reader reader = new StringReader(csvString)) {
            CSVParser parser = CSVParser.parse(reader, CSVFormat.DEFAULT);
            packages.put(
                    Architecture.current(),
                    new Packages(
                            // Keep the latest version only. CentOS can keep multiple versions installed, e.g. kernel-core
                            Packages.getUniquePackagesWithMaxVersion(parser.getRecords().stream()
                                    .map(record -> new UnchangingPackage(
                                            record.get(0),
                                            record.get(1),
                                            record.get(2),
                                            record.get(3)
                                    ))
                                    .toList()
                            )
                    )
            );
        }

        Optional<UnchangingContainerReference> newImage = getActualInstructions().stream()
                .filter(each -> each instanceof From)
                .map(each -> (From) each)
                .map(each -> new UnchangingContainerReference(
                        each.getImage(),
                        each.getVersion(),
                        getManifestDigest(each.getReference())
                ))
                .findAny();
        if (newImage.isPresent()) {
            if (image == null) {
                image = new HashMap<>();
            }
            image.put(Architecture.current(), newImage.get());
        } else {
            image = null;
        }

        try (Writer writer = Files.newBufferedWriter(RegularFileUtils.toPath(getLockFileLocation()))) {
            Lockfile.write(
                    new Lockfile(
                            packages,
                            image
                    ),
                    writer
            );
        }
    }

    public String getManifestDigest(String image) {
        if (manifestDigest != null) {
            return manifestDigest;
        }
        DockerUtils daemonActions = new DockerUtils(getExecOperations());
        try (ByteArrayOutputStream stdout = new ByteArrayOutputStream()) {
            daemonActions.exec(spec -> {
                spec.setStandardOutput(stdout);
                spec.commandLine("docker", "manifest", "inspect", image);
            });
            try (InputStreamReader reader = new InputStreamReader(new ByteArrayInputStream(stdout.toByteArray()))) {
                ObjectMapper mapper = new ObjectMapper();
                JsonNode root = mapper.readTree(reader);
                String digest = null;
                Iterator<JsonNode> manifests = root.path("manifests").elements();
                while (manifests.hasNext()) {
                    JsonNode manifest = manifests.next();
                    if (Architecture.current().dockerName().equals(manifest.path("platform").path("architecture").asText())) {
                        digest = manifest.path("digest").asText(null);
                        break;
                    }
                }
                if (digest == null) {
                    // We could make this work for a single platform if we really wanted to, for now it's an error
                    throw new GradleException("Can't find manifest digest from docker output. " +
                                              "Does the image have a manifest?\n" + stdout
                    );
                }
                manifestDigest = digest;
                return digest;
            }
        } catch (IOException e) {
            throw new UncheckedIOException("Failed to read the image manifest", e);
        }
    }

    private Path writeScript(Path dir) {
        InputStream resourceStream = getClass().getResourceAsStream(String.format("/%s", PRINT_INSTALLED_PACKAGES_NAME));
        if (resourceStream == null) {
            throw new GradleException(
                    String.format("Could not find an embedded resource for %s", PRINT_INSTALLED_PACKAGES_NAME));
        }
        try {
            final Path csvGenScript = dir.resolve(PRINT_INSTALLED_PACKAGES_NAME);
            Files.copy(
                    resourceStream, csvGenScript,
                    StandardCopyOption.REPLACE_EXISTING
            );
            Files.setPosixFilePermissions(
                    csvGenScript,
                    Set.of(
                            PosixFilePermission.OWNER_READ,
                            PosixFilePermission.OTHERS_READ,
                            PosixFilePermission.OTHERS_EXECUTE,
                            PosixFilePermission.OWNER_EXECUTE
                    )
            );
            return csvGenScript;
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

}
