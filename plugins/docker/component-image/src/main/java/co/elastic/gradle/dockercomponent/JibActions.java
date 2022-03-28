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

package co.elastic.gradle.dockercomponent;

import co.elastic.gradle.utils.RetryUtils;
import co.elastic.gradle.utils.docker.instruction.*;
import com.google.cloud.tools.jib.api.CacheDirectoryCreationException;
import com.google.cloud.tools.jib.api.Containerizer;
import com.google.cloud.tools.jib.api.CredentialRetriever;
import com.google.cloud.tools.jib.api.DockerDaemonImage;
import com.google.cloud.tools.jib.api.ImageReference;
import com.google.cloud.tools.jib.api.InvalidImageReferenceException;
import com.google.cloud.tools.jib.api.Jib;
import com.google.cloud.tools.jib.api.JibContainer;
import com.google.cloud.tools.jib.api.JibContainerBuilder;
import com.google.cloud.tools.jib.api.RegistryException;
import com.google.cloud.tools.jib.api.RegistryImage;
import com.google.cloud.tools.jib.api.TarImage;
import com.google.cloud.tools.jib.api.buildplan.AbsoluteUnixPath;
import com.google.cloud.tools.jib.api.buildplan.FileEntriesLayer;
import com.google.cloud.tools.jib.api.buildplan.FilePermissions;
import com.google.cloud.tools.jib.api.buildplan.Port;
import com.google.cloud.tools.jib.frontend.CredentialRetrieverFactory;
import com.google.common.jimfs.Configuration;
import com.google.common.jimfs.Jimfs;
import org.apache.commons.compress.compressors.zstandard.ZstdCompressorOutputStream;
import org.apache.commons.io.IOUtils;
import org.gradle.api.GradleException;
import org.gradle.api.file.RegularFile;
import org.jetbrains.annotations.NotNull;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.*;
import java.nio.file.FileSystem;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.attribute.PosixFilePermission;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.ExecutionException;
import java.util.stream.Stream;

public class JibActions {

    private Logger logger = LoggerFactory.getLogger(JibActions.class);

    public String getImageId(String reference) {
        return RetryUtils.retry(() -> {
                    try {
                        return Jib.from(getAuthenticatedRegistryImage(reference))
                                .containerize(getContainerizer(
                                        TarImage.at(new File("/dev/null").toPath()).named("dev/null")
                                ))
                                .getImageId()
                                .toString();
                    } catch (InterruptedException | InvalidImageReferenceException | RegistryException | IOException | CacheDirectoryCreationException | ExecutionException e) {
                        throw new GradleException("Failed to get Image IDs of remote images", e);
                    }
                }).maxAttempt(6)
                .exponentialBackoff(1000, 30000)
                .onRetryError(error -> logger.warn("Error while pushing image with Jib. Retrying", error))
                .execute();
    }

    private RegistryImage getAuthenticatedRegistryImage(String reference) throws InvalidImageReferenceException {
        final ImageReference imageRef = ImageReference.parse(reference);
        return RegistryImage.named(imageRef)
                .addCredentialRetriever(
                        getCredentialRetriever(imageRef)
                );
    }

    private CredentialRetriever getCredentialRetriever(ImageReference parse) {
        return CredentialRetrieverFactory.forImage(
                parse,
                credentialEvent -> logger.info(credentialEvent.getMessage())
        ).dockerConfig();
    }

    public JibContainer pushImage(Path imageArchive, String tag, Instant createdAt) {
        return RetryUtils.retry(() -> {
                    try {
                        return Jib.from(TarImage.at(imageArchive))
                                .setCreationTime(createdAt)
                                .containerize(
                                        Containerizer.to(getAuthenticatedRegistryImage(tag))
                                );
                    } catch (InterruptedException | RegistryException | IOException | CacheDirectoryCreationException | ExecutionException | InvalidImageReferenceException e) {
                        throw new GradleException("Error pushing image archive in registry (" + tag + ").", e);
                    }
                }).maxAttempt(6)
                .exponentialBackoff(1000, 30000)
                .onRetryError(error -> logger.warn("Error while pushing image with Jib. Retrying", error))
                .execute();
    }

    public void buildTo(String localDockerDaemonTag, RegularFile imageId, List<ContainerImageBuildInstruction> instructions, Path contextRoot) {
        final Optional<FromLocalArchive> fromLocalImageBuild = instructions.stream()
                .filter(instruction -> instruction instanceof FromLocalArchive)
                .map(it -> (FromLocalArchive) it)
                .findFirst();
        final JibContainerBuilder jibBuilder;
        if (fromLocalImageBuild.isPresent()) {
            jibBuilder = Jib.from(
                    TarImage.at(fromLocalImageBuild.get().getImageArchive().get().toPath())
            );
        } else {
            jibBuilder = Jib.fromScratch();
        }

        processInstructions(
                jibBuilder,
                contextRoot,
                instructions
        );

        // We need the image ID to stay constant when the inputs don't change so that we can use it for build avoidance,
        // but the creation time poses some challenges in this regard since it causes it to be always different.
        // Jib builds directly to the local daemon, so we don't really have anything to cache here. While we could
        // build a tar that can be cached and then import it using the cli that will have a very high overhead, require
        // more storage in the cache node and will be slower to run. The process of building the image is deterministic
        // the timestamp is the only reason it changes. The simplest solution is to set the creation time to a fixed
        // value. It can be aby value as long as it's fixed. Setting it to the unix epoch is the most likely to signal
        // that this is not set. Note that images pushed to the registry don't take this code path and will have a correct
        // creation time.
        jibBuilder.setCreationTime(Instant.ofEpochMilli(0));
        try {
            final JibContainer container;

            container = jibBuilder.containerize(
                    Containerizer.to(DockerDaemonImage.named(localDockerDaemonTag))
                            .setApplicationLayersCache(getJibCacheDir())
                            .setBaseImageLayersCache(getJibCacheDir())
            );

            Files.writeString(
                    imageId.getAsFile().toPath(),
                    container.getImageId().getHash()
            );
        } catch (IOException | InvalidImageReferenceException | InterruptedException | RegistryException | CacheDirectoryCreationException | ExecutionException e) {
            throw new GradleException("Failed to import component image", e);
        }
    }

    public void buildTo(RegularFile imageArchive, RegularFile imageId, RegularFile createdAtFile, List<ContainerImageBuildInstruction> instructions) {
        try {
            final Optional<From> fromImageRef = instructions.stream()
                    .filter(instruction -> instruction instanceof From)
                    .map(it -> (From) it)
                    .findFirst();

            final JibContainerBuilder jibBuilder;
            if (fromImageRef.isPresent()) {
                try {
                    jibBuilder = Jib.from(
                            getAuthenticatedRegistryImage(fromImageRef.get().getReference())
                    );
                } catch (InvalidImageReferenceException e) {
                    throw new GradleException("Invalid from image format", e);
                }
            } else {
                jibBuilder = Jib.fromScratch();
            }
            processInstructions(
                    jibBuilder,
                    imageArchive.getAsFile().toPath().getParent(),
                    instructions
            );

            Instant createdAt = Instant.now();
            jibBuilder.setCreationTime(createdAt);

            final JibContainer container;
            try (FileSystem memFS = Jimfs.newFileSystem(Configuration.unix())) {
                final Path imagePath = memFS.getPath("/foo");
                container = jibBuilder.containerize(
                        getContainerizer(TarImage.at(imagePath).named("detached"))
                );
                try (InputStream image = Files.newInputStream(imagePath); ZstdCompressorOutputStream compressedOut = new ZstdCompressorOutputStream(
                        new BufferedOutputStream(Files.newOutputStream(imageArchive.getAsFile().toPath())))) {
                    IOUtils.copy(image, compressedOut);
                }
            }
            Files.writeString(
                    imageId.getAsFile().toPath(),
                    container.getImageId().getHash()
            );

            Files.writeString(
                    createdAtFile.getAsFile().toPath(),
                    createdAt.toString()
            );
        } catch (InterruptedException | RegistryException | IOException | CacheDirectoryCreationException | ExecutionException | InvalidImageReferenceException e) {
            throw new GradleException("Failed to build component image", e);
        }
    }

    private void processInstructions(JibContainerBuilder jibBuilder, Path contextRoot, List<ContainerImageBuildInstruction> instructions) {
        // We don't support  setting labels on base images, don't inherit them, these are component specific
        // TODO: This doesn't really set it to empty, we do it in the build script for now, but would be worth looking at
        jibBuilder.setLabels(Collections.emptyMap());
        jibBuilder.setEntrypoint((List<String>) null);
        jibBuilder.setProgramArguments((List<String>) null);
        jibBuilder.setExposedPorts(Collections.emptySet());

        instructions.stream()
                .filter(t -> !(t instanceof FromLocalArchive))
                .filter(t -> !(t instanceof From))
                .forEach(instruction -> applyJibInstruction(
                        jibBuilder,
                        instruction,
                        contextRoot
                ));
    }

    private Containerizer getContainerizer(TarImage at) {
        return Containerizer.to(at)
                .setApplicationLayersCache(getJibCacheDir())
                .setBaseImageLayersCache(getJibCacheDir());
    }

    @NotNull
    private Path getJibCacheDir() {
        return Paths.get(System.getProperty("java.io.tmpdir")).resolve(".jib");
    }

    private void applyJibInstruction(JibContainerBuilder jibBuilder, ContainerImageBuildInstruction instruction, Path contextPath) {
        if (instruction instanceof Copy) {
            Copy copyInstruction = (Copy) instruction;
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
        } else if (instruction instanceof Entrypoint entrypoint) {
            jibBuilder.setEntrypoint(entrypoint.getValue());
        } else if (instruction instanceof Cmd cmd) {
            jibBuilder.setProgramArguments(cmd.getValue());
        } else if (instruction instanceof Env envInstruction) {
            jibBuilder.addEnvironmentVariable(envInstruction.getKey(), envInstruction.getValue());
        } else if (instruction instanceof Expose expose) {
            switch (expose.getType()) {
                case TCP -> jibBuilder.addExposedPort(Port.tcp(expose.getPort()));
                case UDP -> jibBuilder.addExposedPort(Port.udp(expose.getPort()));
            }
        } else if (instruction instanceof Label label) {
            jibBuilder.addLabel(label.getKey(), label.getValue());
        } else if (instruction instanceof ChangingLabel changingLabel) {
            jibBuilder.addLabel(changingLabel.getKey(), changingLabel.getValue());
        } else if (instruction instanceof Maintainer maintainer) {
            jibBuilder.addLabel("maintainer", maintainer.getName() + "<" + maintainer.getEmail() + ">");
        } else if (instruction instanceof Workdir) {
            jibBuilder.setWorkingDirectory(AbsoluteUnixPath.get(((Workdir) instruction).getFolder()));
        } else {
            throw new GradleException("Instruction " + instruction + "is not a valid Jib instruction");
        }
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

}
