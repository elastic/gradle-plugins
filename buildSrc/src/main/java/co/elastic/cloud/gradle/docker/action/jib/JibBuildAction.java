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

import co.elastic.cloud.gradle.docker.DockerBuildContext;
import co.elastic.cloud.gradle.docker.DockerFileExtension;
import co.elastic.cloud.gradle.docker.build.DockerBuildInfo;
import com.google.cloud.tools.jib.api.*;
import com.google.cloud.tools.jib.api.buildplan.AbsoluteUnixPath;
import com.google.cloud.tools.jib.api.buildplan.FileEntriesLayer;
import com.google.cloud.tools.jib.api.buildplan.FilePermissions;
import com.google.gson.Gson;
import org.gradle.api.GradleException;
import org.gradle.api.Project;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.PosixFilePermission;
import java.util.Arrays;
import java.util.HashSet;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ExecutionException;

public class JibBuildAction {

    private final DockerFileExtension extension;
    private final Project project;

    public JibBuildAction(DockerFileExtension extension, Project project) {
        this.extension = extension;
        this.project = project;
    }

    public void build(String imageTag) {
        try {
            ImageReference imageReference = JibUtil.parse(imageTag);
            // Base image is the tar archive stored by dockerBuild of another project if referenced
            // or the baseImage path stored by the dockerJibPull of this project
            JibContainerBuilder jibBuilder = Jib.from(
                    TarImage.at(
                            getExtension().getFromProject().map(project -> new DockerBuildContext(project, "dockerBuild").projectTarImagePath()) // TODO should be able to retrieve it from project instead of recreating it
                                    .orElseGet(() -> getExtension().getContext().jibBaseImagePath())));

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
                                    throw new GradleException("Error configuring layer" + ordinal + " for Jib docker image", e);
                                }
                            });
                        } else {
                            throw new GradleException("Error in copy configuration : layer" + ordinal + " is not an existing folder.");
                        }
                    }
            );

            Optional.ofNullable(getExtension().getEntryPoint())
                    .ifPresent(jibBuilder::setEntrypoint);

            Optional.ofNullable(getExtension().getCmd())
                    .ifPresent(jibBuilder::setProgramArguments);

            Optional.ofNullable(getExtension().getLabels())
                    .ifPresent(labels -> labels.forEach(jibBuilder::addLabel));

            Optional.ofNullable(getExtension().getEnv())
                    .ifPresent(envs -> envs.forEach(jibBuilder::addEnvironmentVariable));


            JibContainer jibContainer = jibBuilder.containerize(
                    Containerizer
                            .to(TarImage.at(getProjectImageArchive()).named(imageReference))
                            .setApplicationLayersCache(getApplicationLayerCache()));

            try (FileWriter writer = new FileWriter(getExtension().getContext().imageBuildInfo().toFile())) {
                writer.write(new Gson().toJson(new DockerBuildInfo()
                        .setTag(imageReference.toString())
                        .setBuilder(DockerBuildInfo.Builder.JIB)
                        .setImageId(jibContainer.getImageId().getHash())));

            } catch (IOException e) {
                throw new GradleException("Error writing image info file", e);
            }
        } catch (InterruptedException | RegistryException | IOException | CacheDirectoryCreationException | ExecutionException e) {
            throw new GradleException("Error running Jib docker image build", e);
        }
    }

    public void cleanAndBuild(String imageTag) {
        // Clean application cache before build to avoid useless application layer in the cache
        getProject().delete(getApplicationLayerCache());
        build(imageTag);
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
}
