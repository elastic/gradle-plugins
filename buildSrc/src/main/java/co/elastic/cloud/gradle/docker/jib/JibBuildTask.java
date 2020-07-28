package co.elastic.cloud.gradle.docker.jib;

import co.elastic.cloud.gradle.docker.*;
import com.google.cloud.tools.jib.api.*;
import com.google.cloud.tools.jib.api.buildplan.AbsoluteUnixPath;
import com.google.cloud.tools.jib.api.buildplan.FileEntriesLayer;
import org.gradle.api.DefaultTask;
import org.gradle.api.GradleException;
import org.gradle.api.tasks.*;

import java.io.File;
import java.io.IOException;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.Optional;
import java.util.concurrent.ExecutionException;

@CacheableTask
public class JibBuildTask extends DefaultTask {

    private final Path applicationLayerCache;
    private final Path projectImageArchive;
    private DockerBuildExtension extension;

    public JibBuildTask() {
        super();
        this.applicationLayerCache = DockerPluginConventions.jibApplicationLayerCachePath(getProject());
        this.projectImageArchive = DockerPluginConventions.projectTarImagePath(getProject());
    }

    @TaskAction
    public void build() {
        try {
            // Base image is the tar archive stored by dockerBuild of another project if referenced
            // or the baseImage path stored by the dockerJibPull of this project
            JibContainerBuilder jibBuilder = Jib.from(
                    TarImage.at(
                            extension.getFromProject().map(DockerPluginConventions::projectTarImagePath)
                                    .orElseGet(() -> DockerPluginConventions.jibBaseImagePath(getProject()))));

            Optional.ofNullable(getExtension().getMaintainer())
                    .ifPresent(maintainer -> jibBuilder.addLabel("maintainer", maintainer));

            getExtension().forEachCopyLayer(
                    (ordinal, _action) -> {
                        // We can't add directly to / causing a NPE in Jib
                        // We need to walk through the contexts to add them separately => https://github.com/GoogleContainerTools/jib/issues/2195
                        File contextFolder = DockerPluginConventions.contextPath(getProject()).resolve("layer" + ordinal).toFile();
                        if (contextFolder.exists() && contextFolder.isDirectory() && contextFolder.listFiles().length > 0) {
                            Arrays.stream(contextFolder.listFiles()).forEach(file -> {
                                try {
                                    jibBuilder.addFileEntriesLayer(FileEntriesLayer.builder().addEntryRecursive(file.toPath(), AbsoluteUnixPath.get("/" + file.getName())).build());
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

            Optional.ofNullable(getExtension().getLabel())
                    .ifPresent(labels -> labels.forEach(jibBuilder::addLabel));

            Optional.ofNullable(getExtension().getEnv())
                    .ifPresent(envs -> envs.forEach(jibBuilder::addEnvironmentVariable));

            ImageReference imageReference = DockerPluginConventions.imageReference(getProject());
            JibContainer jibContainer = jibBuilder.containerize(
                    Containerizer
                            .to(TarImage.at(getProjectImageArchive()).named(imageReference))
                            .setApplicationLayersCache(getApplicationLayerCache()));



            getProject().getExtensions().add(DockerBuildResultExtension.class,
                    "dockerBuildResult",
                    new DockerBuildResultExtension(jibContainer.getImageId().toString(), getProjectImageArchive()));
        } catch (InterruptedException | RegistryException | IOException | CacheDirectoryCreationException | ExecutionException e) {
            throw new GradleException("Error running Jib docker image build", e);
        }
    }

    @OutputDirectory
    public Path getApplicationLayerCache() {
        return applicationLayerCache;
    }

    @Internal
    public Path getProjectImageArchive() {
        return projectImageArchive;
    }

    @Nested
    public DockerBuildExtension getExtension() {
        return extension;
    }

    public void setExtension(DockerBuildExtension extension) {
        this.extension = extension;
    }

}
