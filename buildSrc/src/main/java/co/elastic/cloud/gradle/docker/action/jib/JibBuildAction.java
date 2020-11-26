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

package co.elastic.cloud.gradle.docker.action.jib;

import co.elastic.cloud.gradle.docker.DockerFileExtension;
import co.elastic.cloud.gradle.docker.build.DockerBuildInfo;
import co.elastic.cloud.gradle.docker.build.DockerImageExtension;
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
import com.google.cloud.tools.jib.json.JsonTemplate;
import com.google.cloud.tools.jib.json.JsonTemplateMapper;
import com.google.cloud.tools.jib.tar.TarExtractor;
import org.gradle.api.GradleException;
import org.gradle.api.Project;

import javax.annotation.Nullable;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.PosixFilePermission;
import java.util.*;
import java.util.concurrent.ExecutionException;

public class JibBuildAction {

    private final DockerFileExtension extension;
    private final Project project;

    public JibBuildAction(DockerFileExtension extension, Project project) {
        this.extension = extension;
        this.project = project;
    }

    public DockerBuildInfo build(String imageTag) {
        try {
            ImageReference imageReference = JibUtil.parse(imageTag);

            Path parentImagePath = getExtension().getFromProject()
                    .flatMap(project -> Optional.ofNullable(project.getExtensions().findByType(DockerImageExtension.class)))
                    .map(ext -> ext.getContext().projectTarImagePath())
                    .orElseGet(() -> getExtension().getContext().jibBaseImagePath());

            // Base config is the tar archive stored by dockerBuild of another project if referenced
            // or the baseImage path stored by the dockerJibPull of this project
            JibContainerBuilder jibBuilder = Jib.from(TarImage.at(parentImagePath));

            // Load parent configuration from base image tar or parent project image tar
            ContainerConfiguration parentConfig = readConfigurationFromTar(parentImagePath);


            Optional.ofNullable(getExtension().getMaintainer())
                    .ifPresent(maintainer -> jibBuilder.addLabel("maintainer", maintainer));

            getExtension().forEachCopyLayer(
                    (ordinal, _action) -> {
                        // We can't add directly to / causing a NPE in Jib
                        // We need to walk through the contexts to add them separately => https://github.com/GoogleContainerTools/jib/issues/2195
                        File contextFolder = getExtension().getContext().contextPath().resolve("layer" + ordinal).toFile();
                        if (contextFolder.exists() && contextFolder.isDirectory() && contextFolder.listFiles().length > 0) {
                            Arrays.stream(contextFolder.listFiles()).forEach(file -> {
                                try {
                                    jibBuilder.addFileEntriesLayer(
                                            FileEntriesLayer.builder()
                                                    .addEntryRecursive(
                                                            file.toPath(),
                                                            AbsoluteUnixPath.get("/" + file.getName()),
                                                            JibBuildAction::getJibFilePermission,
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
                    Optional.ofNullable(getExtension().getEntryPoint()).filter(it -> !it.isEmpty()),
                    Optional.ofNullable(parentConfig.getEntryPoint())
            ).ifPresent(jibBuilder::setEntrypoint);

            or(
                    Optional.ofNullable(getExtension().getCmd()).filter(it -> !it.isEmpty()),
                    Optional.ofNullable(parentConfig.getCmd())
            ).ifPresent(jibBuilder::setProgramArguments);

            Optional.ofNullable(getExtension().getLabels())
                    .ifPresent(labels -> labels.forEach(jibBuilder::addLabel));

            Optional.ofNullable(getExtension().getEnv())
                    .ifPresent(envs -> envs.forEach(jibBuilder::addEnvironmentVariable));

            Optional.ofNullable(getExtension().getWorkDir()).ifPresent(workingDirectory ->
                    jibBuilder.setWorkingDirectory(AbsoluteUnixPath.get(workingDirectory))
            );

            getExtension().getExposeTcp().stream()
                    .map(Port::tcp)
                    .forEach(jibBuilder::addExposedPort);
            getExtension().getExposeUdp().stream()
                    .map(Port::udp)
                    .forEach(jibBuilder::addExposedPort);

            JibContainer jibContainer = jibBuilder.containerize(
                    Containerizer
                            .to(TarImage.at(getProjectImageArchive()).named(imageReference))
                            .setApplicationLayersCache(getApplicationLayerCache()));

            return new DockerBuildInfo()
                    .setTag(imageReference.toString())
                    .setBuilder(DockerBuildInfo.Builder.JIB)
                    .setImageId(jibContainer.getImageId().toString());
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

    public DockerFileExtension getExtension() {
        return extension;
    }

    public Project getProject() {
        return project;
    }

    public Path getApplicationLayerCache() {
        return getExtension().getContext().jibApplicationLayerCachePath();
    }

    public Path getProjectImageArchive() {
        return getExtension().getContext().projectTarImagePath();
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
            throw new GradleException("Error reading config configuration from " + tarPath, e);
        }
    }

    // Jib visibility issue to use com.google.cloud.tools.jib.image.json.ContainerConfigurationTemplate for entrypoint & cmd
    @JsonIgnoreProperties(ignoreUnknown = true)
    private static class ContainerConfiguration implements JsonTemplate {
        private final ConfigurationObject config = new ConfigurationObject();

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
