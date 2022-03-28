package co.elastic.gradle.dockerbase;

import co.elastic.gradle.utils.docker.ContainerImageProviderTask;
import co.elastic.gradle.utils.docker.DockerUtils;
import org.apache.commons.io.output.NullOutputStream;
import org.gradle.api.DefaultTask;
import org.gradle.api.GradleException;
import org.gradle.api.file.RegularFileProperty;
import org.gradle.api.model.ObjectFactory;
import org.gradle.api.provider.Property;
import org.gradle.api.tasks.*;
import org.gradle.process.ExecOperations;
import org.gradle.process.ExecResult;

import javax.inject.Inject;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;

abstract public class DockerLocalImportArchiveTask extends DefaultTask implements ContainerImageProviderTask {


    private boolean imageExistsInDaemon(DockerUtils daemonActions, String imageId) throws IOException {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        ExecResult result = daemonActions.exec(spec -> {
            spec.commandLine("docker", "image", "inspect", "--format", "{{.Id}}", imageId);
            spec.setStandardOutput(out);
            spec.setErrorOutput(NullOutputStream.NULL_OUTPUT_STREAM);
            spec.setIgnoreExitValue(true);
        });
        return result.getExitValue() == 0;
    }

    @TaskAction
    public void localImport() throws IOException {
        DockerUtils dockerUtils = new DockerUtils(getExecOperations());
        if (imageExistsInDaemon(dockerUtils, getImageId().get())) {
            getLogger().lifecycle("Docker Daemon already has image with Id {}. Skip import.", getImageId().get());
        } else {
            try (InputStream archiveInput = DockerDaemonActions.readDockerImage(getImageArchive().get().getAsFile().toPath())) {
                dockerUtils.exec(spec -> {
                    spec.setStandardInput(archiveInput);
                    spec.commandLine("docker", "load");
                });
            } catch (IOException e) {
                throw new GradleException("Error importing image in docker daemon", e);
            }
        }
        // The image might exist, but we want to make sure it's still tagged as we want it to
        dockerUtils.exec(spec -> {
            spec.commandLine("docker", "tag", getImageId().get(), getTag().get());
        });
        getLogger().lifecycle("Image tagged as {}", getTag().get());
        Files.write(getMarker().toPath(), getTag().get().getBytes(StandardCharsets.UTF_8));
    }

    @Override
    @Input
    public abstract Property<String> getTag();

    @OutputFile
    public File getMarker() {
        return new File(getProject().getBuildDir(), "docker_local_images/" + getName());
    }

    @InputFile
    @PathSensitive(PathSensitivity.RELATIVE)
    abstract public RegularFileProperty getImageArchive();

    @Input
    abstract public Property<String> getImageId();

    @Inject
    public ExecOperations getExecOperations() {
        throw new IllegalStateException("not implemented");
    }

    @Inject
    public ObjectFactory getObjectFactory() {
        throw new IllegalStateException("not implemented");
    }


}
