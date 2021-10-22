/*
 *
 *  * ELASTICSEARCH CONFIDENTIAL
 *  * __________________
 *  *
 *  *  Copyright Elasticsearch B.V. All rights reserved.
 *  *
 *  * NOTICE:  All information contained herein is, and remains
 *  * the property of Elasticsearch B.V. and its suppliers, if any.
 *  * The intellectual and technical concepts contained herein
 *  * are proprietary to Elasticsearch B.V. and its suppliers and
 *  * may be covered by U.S. and Foreign Patents, patents in
 *  * process, and are protected by trade secret or copyright
 *  * law.  Dissemination of this information or reproduction of
 *  * this material is strictly forbidden unless prior written
 *  * permission is obtained from Elasticsearch B.V.
 *
 */

package co.elastic.cloud.gradle.docker.action;

import co.elastic.cloud.gradle.dockerbase.JibInstruction;
import co.elastic.cloud.gradle.util.RetryUtils;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.MapperFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.cloud.tools.jib.api.*;
import com.google.cloud.tools.jib.api.buildplan.AbsoluteUnixPath;
import com.google.cloud.tools.jib.api.buildplan.FileEntriesLayer;
import com.google.cloud.tools.jib.api.buildplan.FilePermissions;
import com.google.cloud.tools.jib.api.buildplan.Port;
import com.google.cloud.tools.jib.docker.json.DockerManifestEntryTemplate;
import com.google.cloud.tools.jib.filesystem.TempDirectoryProvider;
import com.google.cloud.tools.jib.frontend.CredentialRetrieverFactory;
import com.google.cloud.tools.jib.json.JsonTemplate;
import com.google.cloud.tools.jib.json.JsonTemplateMapper;
import com.google.common.jimfs.Configuration;
import com.google.common.jimfs.Jimfs;
import org.apache.commons.compress.compressors.zstandard.ZstdCompressorOutputStream;
import org.apache.commons.io.IOUtils;
import org.gradle.api.GradleException;
import org.gradle.api.file.RegularFile;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.annotation.Nullable;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.FileSystem;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.attribute.PosixFilePermission;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.ExecutionException;
import java.util.function.Consumer;
import java.util.stream.Stream;

public class JibActions {

    private Logger logger = LoggerFactory.getLogger(JibActions.class);

    public void pull(String from, Path into, Consumer<Exception> onRetryError) {
        RetryUtils.retry(() -> {
                    try {
                        return Jib.from(from)
                                .containerize(
                                        Containerizer.to(TarImage.at(into).named(from)));
                    } catch (InterruptedException | RegistryException | IOException | CacheDirectoryCreationException | ExecutionException | InvalidImageReferenceException e) {
                        throw new GradleException("Error pulling " + from + " through Jib", e);
                    }
                }).maxAttempt(3)
                .exponentialBackoff(1000, 30000)
                .onRetryError(onRetryError)
                .execute();
    }

    private static FilePermissions getJibFilePermission(Path sourcePath, AbsoluteUnixPath target) {
        try {
            return FilePermissions.fromPosixFilePermissions(Files.getPosixFilePermissions(sourcePath));
        } catch (UnsupportedOperationException e) {
            Set<PosixFilePermission> permissions = new HashSet<>();
            File sourceFile = sourcePath.toFile();
            if (sourceFile.canRead()) {
                permissions.add(PosixFilePermission.OWNER_READ);
                permissions.add(PosixFilePermission.GROUP_READ);
                permissions.add(PosixFilePermission.OTHERS_READ);
            }
            if (sourceFile.canWrite()) {
                permissions.add(PosixFilePermission.OWNER_WRITE);
                permissions.add(PosixFilePermission.GROUP_WRITE);
            }
            if (sourceFile.canExecute() || sourceFile.isDirectory()) {
                permissions.add(PosixFilePermission.OWNER_EXECUTE);
                permissions.add(PosixFilePermission.GROUP_EXECUTE);
                permissions.add(PosixFilePermission.OTHERS_EXECUTE);
            }
            return FilePermissions.fromPosixFilePermissions(permissions);
        } catch (IOException | SecurityException e) {
            throw new GradleException("Error while detecting permissions for " + sourcePath.toString(), e);
        }
    }

    public JibContainer pushImage(Path imageArchive, String tag, Instant createdAt) {
        final JibContainer container = RetryUtils.retry(() -> {
                    try {
                        ImageReference imageReference = ImageReference.parse(tag);
                        return Jib.from(TarImage.at(imageArchive))
                                .setCreationTime(createdAt)
                                .containerize(
                                        Containerizer.to(
                                                RegistryImage.named(imageReference)
                                                        .addCredentialRetriever(
                                                                CredentialRetrieverFactory.forImage(
                                                                        imageReference,
                                                                        credentialEvent -> logger.info(credentialEvent.getMessage())
                                                                ).dockerConfig()
                                                        )
                                        )
                                );
                    } catch (InterruptedException | RegistryException | IOException | CacheDirectoryCreationException | ExecutionException | InvalidImageReferenceException e) {
                        throw new GradleException("Error pushing image archive in registry (" + tag + ").", e);
                    }
                }).maxAttempt(6)
                .exponentialBackoff(1000, 30000)
                .onRetryError(error -> logger.warn("Error while pushing image with Jib. Retrying", error))
                .execute();
        return container;
    }

    public void buildTo(RegularFile imageArchive, RegularFile imageId, RegularFile createdAtFile, List<JibInstruction> instructions) {
        try {
            final Optional<JibInstruction.FromLocalImageBuild> fromLocalImageBuild = instructions.stream()
                    .filter(instruction -> instruction instanceof JibInstruction.FromLocalImageBuild)
                    .map(it -> (JibInstruction.FromLocalImageBuild) it)
                    .findFirst();

            final Optional<JibInstruction.From> fromImageRef = instructions.stream()
                    .filter(instruction -> instruction instanceof JibInstruction.From)
                    .map(it -> (JibInstruction.From) it)
                    .findFirst();

            final JibContainerBuilder jibBuilder;
            if (fromLocalImageBuild.isPresent()) {
                jibBuilder = Jib.from(
                        TarImage.at(fromLocalImageBuild.get().getImageArchive().get().toPath())
                );
            } else if (fromImageRef.isPresent()) {
                try {
                    final ImageReference imageReference = ImageReference.parse(fromImageRef.get().getReference());
                    jibBuilder = Jib.from(
                            RegistryImage.named(imageReference)
                                    .addCredentialRetriever(
                                            CredentialRetrieverFactory.forImage(
                                                    imageReference,
                                                    e -> {
                                                        logger.info(e.getMessage());
                                                    }
                                            ).dockerConfig()
                                    )
                    );
                } catch (InvalidImageReferenceException e) {
                    throw new GradleException("Invalid from image format", e);
                }
            } else {
                jibBuilder = Jib.fromScratch();
            }

            // We don't support  setting labels on base images, don't inherit them, these are component specific
            // TODO: This doesn't really set it to empty, we do it in the build script for now, but would be worth looking at
            jibBuilder.setLabels(Collections.emptyMap());
            jibBuilder.setEntrypoint((List<String>) null);
            jibBuilder.setProgramArguments((List<String>) null);
            jibBuilder.setExposedPorts(Collections.emptySet());

            instructions.stream()
                    .filter(t -> !(t instanceof JibInstruction.FromLocalImageBuild))
                    .filter(t -> !(t instanceof JibInstruction.From))
                    .forEach(instruction -> applyJibInstruction(
                            jibBuilder,
                            instruction,
                            imageArchive.getAsFile().toPath().getParent()
                    ));

            Instant createdAt = Instant.now();
            jibBuilder.setCreationTime(createdAt);

            final JibContainer container;
            try (FileSystem memFS = Jimfs.newFileSystem(Configuration.unix())) {
                final Path imagePath = memFS.getPath("/foo");
                final Path cacheDir = Paths.get(System.getProperty("java.io.tmpdir")).resolve(".jib");
                container = jibBuilder.containerize(
                        Containerizer.to(TarImage.at(imagePath).named("detached"))
                                .setApplicationLayersCache(cacheDir)
                                .setBaseImageLayersCache(cacheDir)
                );
                try (InputStream image = Files.newInputStream(imagePath); ZstdCompressorOutputStream compressedOut = new ZstdCompressorOutputStream(
                        new BufferedOutputStream(Files.newOutputStream(imageArchive.getAsFile().toPath())))) {
                    IOUtils.copy(image, compressedOut);
                }
            }
            Files.write(
                    imageId.getAsFile().toPath(),
                    container.getImageId().getHash().getBytes(StandardCharsets.UTF_8)
            );

            Files.write(
                    createdAtFile.getAsFile().toPath(),
                    createdAt.toString().getBytes(StandardCharsets.UTF_8)
            );
        } catch (InterruptedException | RegistryException | IOException | CacheDirectoryCreationException | ExecutionException | InvalidImageReferenceException e) {
            throw new GradleException("Failed to build component image", e);
        }
    }

    private void applyJibInstruction(JibContainerBuilder jibBuilder, JibInstruction instruction, Path contextPath) {
        if (instruction instanceof JibInstruction.Copy) {
            JibInstruction.Copy copyInstruction = (JibInstruction.Copy) instruction;
            // We can't add directly to / causing a NPE in Jib
            // We need to walk through the contexts to add them separately => https://github.com/GoogleContainerTools/jib/issues/2195
            Path contextFolder = contextPath.resolve(copyInstruction.getLayer());
            if (!Files.exists(contextFolder)) {
                throw new RuntimeException("Input layer " + contextFolder + " does not exsit.");
            }
            if (!Files.isDirectory(contextFolder)) {
                throw new RuntimeException("Expected " + contextFolder + " to be a directory.");
            }
            try (Stream<Path> elements = Files.list(contextFolder)) {
                elements.forEach(file -> {
                            try {
                                jibBuilder.addFileEntriesLayer(
                                        FileEntriesLayer.builder()
                                                .addEntryRecursive(
                                                        file,
                                                        AbsoluteUnixPath.get("/" + file.getFileName()),
                                                        JibActions::getJibFilePermission,
                                                        FileEntriesLayer.DEFAULT_MODIFICATION_TIME_PROVIDER,
                                                        Optional.ofNullable(copyInstruction.getOwner()).isPresent() ?
                                                                (sourcePath, destinationPath) -> copyInstruction.getOwner() :
                                                                FileEntriesLayer.DEFAULT_OWNERSHIP_PROVIDER
                                                ).build());
                            } catch (IOException e) {
                                throw new UncheckedIOException(e);
                            }
                        }
                );
            } catch (IOException e) {
                throw new UncheckedIOException("Error configuring " + copyInstruction.getLayer() + " for Jib docker config", e);
            }
        } else if (instruction instanceof JibInstruction.Entrypoint) {
            JibInstruction.Entrypoint entrypoint = (JibInstruction.Entrypoint) instruction;
            jibBuilder.setEntrypoint(entrypoint.getValue());
        } else if (instruction instanceof JibInstruction.Cmd) {
            JibInstruction.Cmd cmd = (JibInstruction.Cmd) instruction;
            jibBuilder.setProgramArguments(cmd.getValue());
        } else if (instruction instanceof JibInstruction.Env) {
            JibInstruction.Env envInstruction = (JibInstruction.Env) instruction;
            jibBuilder.addEnvironmentVariable(envInstruction.getKey(), envInstruction.getValue());
        } else if (instruction instanceof JibInstruction.Expose) {
            JibInstruction.Expose expose = (JibInstruction.Expose) instruction;
            switch (expose.getType()) {
                case TCP:
                    jibBuilder.addExposedPort(Port.tcp(expose.getPort()));
                    break;
                case UDP:
                    jibBuilder.addExposedPort(Port.udp(expose.getPort()));
                    break;
            }
        } else if (instruction instanceof JibInstruction.Label) {
            JibInstruction.Label label = (JibInstruction.Label) instruction;
            jibBuilder.addLabel(label.getKey(), label.getValue());
        } else if (instruction instanceof JibInstruction.ChangingLabel) {
            JibInstruction.ChangingLabel changingLabel = (JibInstruction.ChangingLabel) instruction;
            jibBuilder.addLabel(changingLabel.getKey(), changingLabel.getValue());
        } else if (instruction instanceof JibInstruction.Maintainer) {
            JibInstruction.Maintainer maintainer = (JibInstruction.Maintainer) instruction;
            jibBuilder.addLabel("maintainer", maintainer.getName() + "<" + maintainer.getEmail() + ">");
        } else if (instruction instanceof JibInstruction.Workdir) {
            jibBuilder.setWorkingDirectory(AbsoluteUnixPath.get(((JibInstruction.Workdir) instruction).getFolder()));
        } else {
            throw new GradleException("Instruction " + instruction + "is not a valid Jib instruction");
        }
    }

    // Jib doesn't add visibility for these utils so recode it
    private ContainerConfiguration readConfigurationFromTar(Path tarPath) {
        try (TempDirectoryProvider tempDirProvider = new TempDirectoryProvider()) {
            Path destination = tempDirProvider.newDirectory();
            Path manifestPath = destination.resolve("manifest.json");
            // extract the manifest
            DockerDaemonActions.extractDockerImage(tarPath, destination,
                    entry -> destination.resolve(entry.getName()).equals(manifestPath));

            try (InputStream manifestStream = Files.newInputStream(manifestPath)) {
                // load the manifest and then only extract the config
                DockerManifestEntryTemplate loadManifest =
                        new ObjectMapper()
                                .configure(MapperFeature.ACCEPT_CASE_INSENSITIVE_PROPERTIES, true)
                                .readValue(manifestStream, DockerManifestEntryTemplate[].class)[0];
                Path configPath = destination.resolve(loadManifest.getConfig());
                DockerDaemonActions.extractDockerImage(tarPath, destination,
                        entry -> destination.resolve(entry.getName()).equals(configPath));
                return JsonTemplateMapper.readJsonFromFile(configPath, ContainerConfiguration.class);
            }
        } catch (IOException e) {
            throw new GradleException(String.format("Error reading container configuration from %s", tarPath), e);
        }
    }

    // Jib visibility issue to use com.google.cloud.tools.jib.image.json.ContainerConfigurationTemplate for entrypoint & cmd
    @JsonIgnoreProperties(ignoreUnknown = true)
    private static class ContainerConfiguration implements JsonTemplate {
        private final ContainerConfiguration.ConfigurationObject config = new ContainerConfiguration.ConfigurationObject();

        @JsonIgnoreProperties(ignoreUnknown = true)
        private static class ConfigurationObject implements JsonTemplate {

            @Nullable
            @JsonProperty("Entrypoint")
            private List<String> entrypoint;

            @Nullable
            @JsonProperty("Cmd")
            private List<String> cmd;
        }

        public List<String> getEntryPoint() {
            return config.entrypoint;
        }

        public List<String> getCmd() {
            return config.cmd;
        }
    }
}
