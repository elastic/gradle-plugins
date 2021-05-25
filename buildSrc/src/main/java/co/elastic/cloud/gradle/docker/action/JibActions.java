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

import co.elastic.cloud.gradle.docker.DockerBuildContext;
import co.elastic.cloud.gradle.docker.DockerFileExtension;
import co.elastic.cloud.gradle.docker.build.DockerBuildInfo;
import co.elastic.cloud.gradle.docker.build.DockerImageExtension;
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
import com.google.cloud.tools.jib.tar.TarExtractor;
import org.gradle.api.GradleException;

import javax.annotation.Nullable;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.PosixFilePermission;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.ExecutionException;
import java.util.function.Consumer;
import java.util.stream.Collectors;

public class JibActions {

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

    public JibContainer push(Path imageArchive, String tag, Instant createdAt, Consumer<LogEvent> onCredentialEvent, Consumer<Exception> onRetryError) {
        ImageReference imageReference = parse(tag);

        return RetryUtils.retry(() -> {
            try {
                return Jib.from(TarImage.at(imageArchive))
                        .setCreationTime(createdAt)
                        .containerize(
                                Containerizer.to(
                                        RegistryImage.named(imageReference)
                                                .addCredentialRetriever(
                                                        CredentialRetrieverFactory.forImage(
                                                                imageReference,
                                                                onCredentialEvent
                                                        ).dockerConfig()
                                                )
                                )
                        );
            } catch (InterruptedException | RegistryException | IOException | CacheDirectoryCreationException | ExecutionException e) {
                throw new GradleException("Error pushing image archive in registry (" + imageReference + ").", e);
            }
        }).maxAttempt(3)
                .exponentialBackoff(1000, 30000)
                .onRetryError(onRetryError)
                .execute();
    }

    private ImageReference parse(String tag) {
        try {
            return ImageReference.parse(tag);
        } catch (InvalidImageReferenceException e) {
            throw new GradleException(tag + " is not a valid Jib ImageRefence", e);
        }
    }

    public DockerBuildInfo build(DockerFileExtension extension, String imageTag) {
        try {
            ImageReference imageReference = parse(imageTag);

            Path parentImagePath = extension.getFromProject()
                    .flatMap(project -> Optional.ofNullable(project.getExtensions().findByType(DockerImageExtension.class)))
                    .map(ext -> ext.getContext().projectTarImagePath())
                    .orElseGet(() -> extension.getContext().jibBaseImagePath());

            // Base config is the tar archive stored by dockerBuild of another project if referenced
            // or the baseImage path stored by the dockerJibPull of this project
            JibContainerBuilder jibBuilder = Jib.from(TarImage.at(parentImagePath));

            Instant createdAt = Optional.ofNullable(extension.getBuildInfo())
                    .map(DockerBuildInfo::getCreatedAt)
                    .orElse(Instant.now());
            jibBuilder.setCreationTime(createdAt);

            // Load parent configuration fromInstruction base image tar or parent project image tar
            ContainerConfiguration parentConfig = readConfigurationFromTar(parentImagePath);


            Optional.ofNullable(extension.getMaintainer())
                    .ifPresent(maintainer -> jibBuilder.addLabel("maintainer", maintainer));

            extension.forEachCopyLayer(
                    (ordinal, _action) -> {
                        // We can't add directly to / causing a NPE in Jib
                        // We need to walk through the contexts to add them separately => https://github.com/GoogleContainerTools/jib/issues/2195
                        File contextFolder = extension.getContext().contextPath().resolve("layer" + ordinal).toFile();
                        if (contextFolder.exists() && contextFolder.isDirectory() && contextFolder.listFiles().length > 0) {
                            Arrays.stream(contextFolder.listFiles()).forEach(file -> {
                                try {
                                    jibBuilder.addFileEntriesLayer(
                                            FileEntriesLayer.builder()
                                                    .addEntryRecursive(
                                                            file.toPath(),
                                                            AbsoluteUnixPath.get("/" + file.getName()),
                                                            JibActions::getJibFilePermission,
                                                            FileEntriesLayer.DEFAULT_MODIFICATION_TIME_PROVIDER,
                                                            _action.owner.isPresent() ?
                                                                    (sourcePath, destinationPath) -> _action.owner.get() :
                                                                    FileEntriesLayer.DEFAULT_OWNERSHIP_PROVIDER
                                                    ).build());
                                } catch (IOException e) {
                                    throw new GradleException("Error configuring layer" + ordinal + " for Jib docker config", e);
                                }
                            });
                        } else {
                            throw new GradleException("Error in copy configuration : layer" + ordinal + " is not an existing folder.");
                        }
                    }
            );

            or(
                    Optional.ofNullable(extension.getEntryPoint()).filter(it -> !it.isEmpty()),
                    Optional.ofNullable(parentConfig.getEntryPoint())
            ).ifPresent(jibBuilder::setEntrypoint);

            or(
                    Optional.ofNullable(extension.getCmd()).filter(it -> !it.isEmpty()),
                    Optional.ofNullable(parentConfig.getCmd())
            ).ifPresent(jibBuilder::setProgramArguments);

            Optional.ofNullable(extension.getLabels())
                    .ifPresent(labels -> labels.forEach(jibBuilder::addLabel));

            Optional.ofNullable(extension.getEnv())
                    .ifPresent(envs -> envs.forEach(jibBuilder::addEnvironmentVariable));

            Optional.ofNullable(extension.getWorkDir()).ifPresent(workingDirectory ->
                    jibBuilder.setWorkingDirectory(AbsoluteUnixPath.get(workingDirectory))
            );

            extension.getExposeTcp().stream()
                    .map(Port::tcp)
                    .forEach(jibBuilder::addExposedPort);
            extension.getExposeUdp().stream()
                    .map(Port::udp)
                    .forEach(jibBuilder::addExposedPort);

            JibContainer jibContainer = jibBuilder.containerize(
                    Containerizer
                            .to(TarImage.at(extension.getContext().projectTarImagePath()).named(imageReference))
                            .setApplicationLayersCache(extension.getContext().jibApplicationLayerCachePath()));

            return new DockerBuildInfo()
                    .setTag(imageReference.toString())
                    .setBuilder(DockerBuildInfo.Builder.JIB)
                    .setImageId(jibContainer.getImageId().toString())
                    .setCreatedAt(createdAt);
        } catch (InterruptedException | RegistryException | IOException | CacheDirectoryCreationException | ExecutionException e) {
            throw new GradleException("Error running Jib docker config build", e);
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

    public DockerBuildInfo buildTo(DockerBuildContext buildContext, List<JibInstruction> instructions, Containerizer target) {
        try {

            Optional<Path> from = instructions.stream()
                    .filter(t -> t instanceof JibInstruction.From).findFirst()
                    .map(t -> {
                        JibInstruction.From fromInstruction = (JibInstruction.From) t;
                        pull(
                                fromInstruction.getImage() + ":" + fromInstruction.getVersion() + "@" + fromInstruction.getSha(),
                                buildContext.jibBaseImagePath(),
                                e -> {
                                    throw new GradleException("Error while pulling base image", e);
                                }
                        );
                        return buildContext.jibBaseImagePath();
                    });

            Optional<Path> fromDockerContext = instructions.stream()
                    .filter(t -> t instanceof JibInstruction.FromDockerBuildContext).findFirst()
                    .map(t -> {
                        JibInstruction.FromDockerBuildContext fromInstruction = (JibInstruction.FromDockerBuildContext) t;
                        return fromInstruction.getBuildContext().projectTarImagePath();
                    });

            Path parentImagePath = or(from, fromDockerContext)
                    .orElseThrow(() -> new GradleException("Jib instructions doesn't define a from value"));
            JibContainerBuilder jibBuilder = Jib.from(TarImage.at(parentImagePath));

            instructions.stream()
                    .filter(t -> !(t instanceof JibInstruction.From || t instanceof JibInstruction.FromDockerBuildContext))
                    .forEach(instruction -> applyJibInstruction(jibBuilder, instruction, buildContext));

            Instant createdAt;
            try {
                DockerBuildInfo buildInfo = buildContext.loadImageBuildInfo();
                createdAt = buildInfo.getCreatedAt();
            } catch (GradleException e) {
                createdAt = Instant.now();
            }
            jibBuilder.setCreationTime(createdAt);

            JibContainer jibContainer = jibBuilder.containerize(target);

            Map<String, String> changingLabels = instructions.stream()
                    .filter(i -> i instanceof JibInstruction.ChangingLabel)
                    .map(i -> (JibInstruction.ChangingLabel) i )
                    .collect(Collectors.toMap(JibInstruction.ChangingLabel::getKey, JibInstruction.ChangingLabel::getValue));

            return new DockerBuildInfo()
                    .setTag(jibContainer.getTags().stream().findFirst().toString())
                    .setBuilder(DockerBuildInfo.Builder.JIB)
                    .setImageId(jibContainer.getImageId().toString())
                    .setRepoDigest(jibContainer.getDigest().toString())
                    .setCreatedAt(createdAt)
                    .setChangingLabels(changingLabels);
        } catch (InterruptedException | RegistryException | IOException | CacheDirectoryCreationException | ExecutionException e) {
            throw new GradleException("Error running Jib docker config build", e);
        }
    }

    private void applyJibInstruction(JibContainerBuilder jibBuilder, JibInstruction instruction, DockerBuildContext buildContext) {
        if (instruction instanceof JibInstruction.Copy) {
            JibInstruction.Copy copyInstruction = (JibInstruction.Copy) instruction;
            // We can't add directly to / causing a NPE in Jib
            // We need to walk through the contexts to add them separately => https://github.com/GoogleContainerTools/jib/issues/2195
            File contextFolder = buildContext.basePath().resolve(copyInstruction.getLayer()).toFile();
            Arrays.stream(contextFolder.listFiles()).forEach(file -> {
                try {
                    jibBuilder.addFileEntriesLayer(
                            FileEntriesLayer.builder()
                                    .addEntryRecursive(
                                            file.toPath(),
                                            AbsoluteUnixPath.get("/" + file.getName()),
                                            JibActions::getJibFilePermission,
                                            FileEntriesLayer.DEFAULT_MODIFICATION_TIME_PROVIDER,
                                            Optional.ofNullable(copyInstruction.getOwner()).isPresent() ?
                                                    (sourcePath, destinationPath) -> copyInstruction.getOwner() :
                                                    FileEntriesLayer.DEFAULT_OWNERSHIP_PROVIDER
                                    ).build());
                } catch (IOException e) {
                    throw new GradleException("Error configuring " + copyInstruction.getLayer() + " for Jib docker config", e);
                }
            });
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
        } else {
            throw new GradleException("Instruction " + instruction + "is not a valid Jib instuction");
        }
    }

    // TODO remove when we add Java 11 for standard JDK
    private static <T> Optional<T> or(Optional<T> x, Optional<T> y) {
        if (x.isPresent()) {
            return x;
        }
        return y;
    }

    // Jib doesn't add visibility for these utils so recode it
    private ContainerConfiguration readConfigurationFromTar(Path tarPath) {
        try (TempDirectoryProvider tempDirProvider = new TempDirectoryProvider()) {
            Path destination = tempDirProvider.newDirectory();
            TarExtractor.extract(tarPath, destination);

            InputStream manifestStream = Files.newInputStream(destination.resolve("manifest.json"));
            DockerManifestEntryTemplate loadManifest =
                    new ObjectMapper()
                            .configure(MapperFeature.ACCEPT_CASE_INSENSITIVE_PROPERTIES, true)
                            .readValue(manifestStream, DockerManifestEntryTemplate[].class)[0];
            manifestStream.close();

            Path configPath = destination.resolve(loadManifest.getConfig());
            return JsonTemplateMapper.readJsonFromFile(configPath, ContainerConfiguration.class);
        } catch (IOException e) {
            throw new GradleException("Error reading config configuration fromInstruction " + tarPath, e);
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
