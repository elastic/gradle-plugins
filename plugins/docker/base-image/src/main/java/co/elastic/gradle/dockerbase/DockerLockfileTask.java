/*
 * Copyright Elasticsearch B.V. and/or licensed to Elasticsearch B.V. under one
 * or more contributor license agreements. See the NOTICE file distributed with
 * this work for additional information regarding copyright
 * ownership. Elasticsearch B.V. licenses this file to you under
 * the Apache License, Version 2.0 (the "License"); you may
 * not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *	http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied.  See the License for the
 * specific language governing permissions and limitations
 * under the License.
 */
package co.elastic.gradle.dockerbase;


import co.elastic.gradle.cli.jfrog.JFrogCliUsingTask;
import co.elastic.gradle.dockerbase.lockfile.BaseLockfile;
import co.elastic.gradle.dockerbase.lockfile.Packages;
import co.elastic.gradle.dockerbase.lockfile.UnchangingPackage;
import co.elastic.gradle.utils.Architecture;
import co.elastic.gradle.utils.RegularFileUtils;
import co.elastic.gradle.utils.RetryUtils;
import co.elastic.gradle.utils.docker.DockerPluginConventions;
import co.elastic.gradle.utils.docker.DockerUtils;
import co.elastic.gradle.utils.docker.UnchangingContainerReference;
import co.elastic.gradle.utils.docker.instruction.*;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.apache.commons.csv.CSVFormat;
import org.apache.commons.csv.CSVParser;
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

import javax.inject.Inject;
import java.io.*;
import java.net.URL;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.attribute.PosixFilePermission;
import java.time.Instant;
import java.util.Optional;
import java.util.*;
import java.util.stream.Collectors;
import java.util.stream.Stream;

public abstract class DockerLockfileTask extends DefaultTask implements ImageBuildable, JFrogCliUsingTask {

    public static final String ARCHIVE_PACKAGES_NAME = "archive-packages.sh";
    private final DefaultCopySpec rootCopySpec;
    private String manifestDigest = null;

    @Inject
    public DockerLockfileTask() {
        getImageIdFile().convention(
                getProjectLayout().getBuildDirectory().file(getName() + "/tmp.image.id")
        );
        getWorkingDirectory().convention(
                getProjectLayout().getBuildDirectory().dir(getName())
        );
        getIsolateFromExternalRepos().convention(false);
        rootCopySpec = getProject().getObjects().newInstance(DefaultCopySpec.class);
        rootCopySpec.addChildSpecListener(DockerPluginConventions.mapCopySpecToTaskInputs(this));
    }

    @Input
    public Instant getCurrentTime() {
        // Task should never be considered up-to-date
        return Instant.now();
    }

    @Override
    @Internal
    public Provider<String> getImageId() {
        //Convenience Provider to access the imageID from the imageIdFile
        return getImageIdFile().map(RegularFileUtils::readString).map(String::trim);
    }

    @Override
    @OutputFile
    public abstract RegularFileProperty getImageIdFile();

    @Override
    @Internal
    public abstract DirectoryProperty getWorkingDirectory();

    @Override
    @Input
    public abstract Property<OSDistribution> getOSDistribution();

    @Override
    @Nested
    public abstract ListProperty<OsPackageRepository> getMirrorRepositories();

    @Override
    @Internal
    public DefaultCopySpec getRootCopySpec() {
        return rootCopySpec;
    }

    @Override
    @Input
    public abstract Property<String> getDockerEphemeralMount();

    @Override
    @InputFile
    @PathSensitive(PathSensitivity.NAME_ONLY)
    public abstract RegularFileProperty getJFrogCli();

    @Override
    @Input
    public abstract Property<Boolean> getIsolateFromExternalRepos();

    @Nested
    public abstract ListProperty<ContainerImageBuildInstruction> getInputInstructions();

    private String getTagWithDigest(String imageReference) {
        final String[] split = imageReference.split(":", 2);
        if (split.length == 2 && split[1].contains("@")) {
            return imageReference;
        } else if (split.length == 2 && !split[1].contains("@")) {
            return String.format(
                    "%s:%s@%s",
                    split[0],
                    split[1],
                    getManifestDigest(imageReference));
        } else {
            throw new IllegalStateException(String.format("Image reference %s unrecognized, must have a tag with our without a digest", imageReference));
        }
    }

    @Override
    @Nested
    public List<ContainerImageBuildInstruction> getActualInstructions() {
        // Use the last available digest for this image
        return Stream.concat(
                        getInputInstructions().get().stream()
                                .map(instruction -> {
                                    if (instruction instanceof From from) {
                                        return new From(getProviderFactory().provider(() ->
                                                getTagWithDigest(from.getReference().get())));
                                    } else {
                                        return instruction;
                                    }
                                }),
                        Stream.concat(
                            getInputInstructions().get().stream().flatMap(i -> {
                                if (i instanceof RepoInstall repoInstall) {
                                    final String packages = repoInstall.getPackages().stream().collect(Collectors.joining(" "));
                                    return Stream.of(
                                            new SetUser("root"),
                                            switch (getOSDistribution().get()) {
                                                case UBUNTU, DEBIAN -> new Run(List.of("apt-get -y --auto-remove purge " + packages));
                                                case CENTOS -> new Run(List.of("yum -y --remove-leaves remove " + packages));
                                                case WOLFI -> throw new GradleException("Wolfi images don't support repoInstall") ;
                                            }
                                    );
                                }
                                return Stream.empty();
                            }),
                            Stream.of(
                                    new SetUser("root"),
                                    DockerDaemonActions.wrapInstallCommand(
                                            this,
                                            switch (getOSDistribution().get()) {
                                                case UBUNTU, DEBIAN -> "apt-get -y --allow-unauthenticated upgrade";
                                                case CENTOS -> "yum -y upgrade";
                                                case WOLFI -> "apk upgrade --no-cache";
                                            }
                                    )
                            )
                        )
                )
                .toList();
    }

    @Input
    public abstract Property<URL> getOsPackageRepository();

    @Inject
    protected abstract ProviderFactory getProviderFactory();

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
        DockerDaemonActions daemonActions = getObjectFactory().newInstance(DockerDaemonActions.class, this);
        DockerUtils dockerUtils = new DockerUtils(getExecOperations());

        final UUID uuid = daemonActions.build();

        final Path archiveScript = writeScript(RegularFileUtils.toPath(getWorkingDirectory()), ARCHIVE_PACKAGES_NAME);

        getLogger().lifecycle(
                "\nRunning the created image to extract package information and upload packages with {} ...",
                getJFrogCli().get().getAsFile().toPath()
        );
        try (ByteArrayOutputStream byteArrayOutputStream = new ByteArrayOutputStream()) {

            final URL repoUrl = getOsPackageRepository().get();
            final Optional<String[]> userinfo = Optional.ofNullable(repoUrl.getUserInfo()).map(it -> it.split(":"));
            String jfrogCLiArgs = String.format(
                    "--retries=25 --retry-wait-time=5s --threads=10 --insecure-tls %s --url %s",
                    userinfo.map(it -> String.format("--user %s --password %s", it[0], it[1])).orElse(""),
                    new URL(repoUrl.toString().replace(repoUrl.getUserInfo() + "@", "") +
                            "/" + getOSDistribution().get().name().toLowerCase(Locale.ROOT)
                    )
            );
            dockerUtils.exec(spec -> {
                spec.setStandardOutput(byteArrayOutputStream);
                spec.setErrorOutput(System.err);
                spec.commandLine(
                        "docker", "run", "--rm",
                        "-v", archiveScript + ":/mnt/" + ARCHIVE_PACKAGES_NAME,
                        "-v", getJFrogCli().get().getAsFile().toPath() + ":/mnt/jfrog-cli",
                        "--entrypoint",  getOSDistribution().get().equals(OSDistribution.WOLFI) ? "/bin/sh" : "/bin/bash",
                        "-eJFROG_CLI_ARGS=" + jfrogCLiArgs,
                        uuid,
                        "/mnt/" + ARCHIVE_PACKAGES_NAME
                );
            });
            writeLockfile(byteArrayOutputStream);
            getLogger().lifecycle("Written new lockfile to {}", getLockFileLocation().get());
        }
        dockerUtils.exec(spec -> spec.commandLine("docker", "image", "rm", uuid));
    }


    private void writeLockfile(ByteArrayOutputStream csvStream) throws IOException {
        final BaseLockfile oldLockfile;
        if (Files.exists(RegularFileUtils.toPath(getLockFileLocation()))) {
            oldLockfile = BaseLockfile.parse(Files.newBufferedReader(RegularFileUtils.toPath(getLockFileLocation())));
        } else {
            oldLockfile = new BaseLockfile(Map.of(), null);
        }
        final Map<Architecture, Packages> packages = new HashMap<>(oldLockfile.getPackages());
        Map<Architecture, UnchangingContainerReference> image;
        if (oldLockfile.getImage() != null) {
            image = new HashMap<>(oldLockfile.getImage());
        } else {
            image = null;
        }

        final String csvString = csvStream.toString().trim();
        if (csvString.isEmpty()) {
            throw new IllegalStateException("Failed to read installed packages from docker image");
        }
        try (Reader reader = new StringReader(csvString)) {
            CSVParser parser = CSVParser.parse(reader, CSVFormat.DEFAULT);
            packages.put(
                    getArchitecture().get(),
                    new Packages(
                            // Keep the latest version only. CentOS can keep multiple versions installed, e.g. kernel-core
                            Packages.getUniquePackagesWithMaxVersion(parser.getRecords().stream()
                                    .map(record -> {
                                        if (record.size() < 4) {
                                            throw new RuntimeException("CSV line from script not valid: " + record.get(0));
                                        }
                                        return new UnchangingPackage(
                                                record.get(0),
                                                record.get(1),
                                                record.get(2),
                                                record.get(3)
                                        );
                                    }).toList()
                            )
                    )
            );
        }

        Optional<UnchangingContainerReference> newImage = getActualInstructions().stream()
                .filter(each -> each instanceof From)
                .map(each -> (From) each)
                .map(each -> {
                    final String[] split = each.getReference().get().split(":", 2);
                    return new UnchangingContainerReference(
                            split[0],
                            // At this point the sha was added to the instructions because we are operating on the
                            // actual instructions
                            split[1].split("@", 2)[0],
                            getManifestDigest(each.getReference().get())
                    );
                })
                .findAny();

        if (newImage.isPresent()) {
            if (image == null) {
                image = new HashMap<>();
            }
            image.put(getArchitecture().get(), newImage.get());
        } else {
            image = null;
        }

        try (Writer writer = Files.newBufferedWriter(RegularFileUtils.toPath(getLockFileLocation()))) {
            BaseLockfile.write(
                    new BaseLockfile(
                            packages,
                            image
                    ),
                    writer
            );
        }
    }

    private String getManifestDigest(String image) {
        if (manifestDigest != null) {
            return manifestDigest;
        }
        DockerUtils daemonActions = new DockerUtils(getExecOperations());
        return RetryUtils.retry(() -> {
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
                                if (getArchitecture().get().dockerName().equals(manifest.path("platform").path("architecture").asText())) {
                                    digest = manifest.path("digest").asText(null);
                                    break;
                                }
                            }
                            if (digest == null) {
                                // Happens when the tag does not point to a manifest list
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
                })
                .maxAttempt(3)
                .exponentialBackoff(1000, 100000)
                .execute();
    }


    private Path writeScript(Path dir, String resource) {
        InputStream resourceStream = getClass().getResourceAsStream(String.format("/%s", resource));
        if (resourceStream == null) {
            throw new GradleException(
                    String.format("Could not find an embedded resource for %s", resource));
        }
        try {
            final Path script = dir.resolve(resource);
            Files.copy(
                    resourceStream, script,
                    StandardCopyOption.REPLACE_EXISTING
            );
            Files.setPosixFilePermissions(
                    script,
                    Set.of(
                            PosixFilePermission.OWNER_READ,
                            PosixFilePermission.OTHERS_READ,
                            PosixFilePermission.OTHERS_EXECUTE,
                            PosixFilePermission.OWNER_EXECUTE
                    )
            );
            return script;
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

}
