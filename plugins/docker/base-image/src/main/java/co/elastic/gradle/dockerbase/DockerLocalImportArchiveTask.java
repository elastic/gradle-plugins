package co.elastic.gradle.dockerbase;

import co.elastic.gradle.utils.ExtractCompressedTar;
import co.elastic.gradle.utils.RegularFileUtils;
import co.elastic.gradle.utils.docker.ContainerImageProviderTask;
import co.elastic.gradle.utils.docker.DockerUtils;
import org.apache.commons.io.output.NullOutputStream;
import org.gradle.api.DefaultTask;
import org.gradle.api.GradleException;
import org.gradle.api.file.ProjectLayout;
import org.gradle.api.file.RegularFileProperty;
import org.gradle.api.provider.Property;
import org.gradle.api.tasks.*;
import org.gradle.process.ExecOperations;
import org.gradle.process.ExecResult;

import javax.inject.Inject;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.util.Objects;

public abstract class DockerLocalImportArchiveTask extends DefaultTask implements ContainerImageProviderTask {

    public DockerLocalImportArchiveTask() {
        getMarker().convention(
                getProjectLayout().getBuildDirectory().file(getName() + ".marker")
        );
    }

    @Override
    @Input
    public abstract Property<String> getTag();

    @InputFile
    @PathSensitive(PathSensitivity.RELATIVE)
    abstract public RegularFileProperty getImageArchive();

    @Input
    abstract public Property<String> getImageId();

    @OutputFile
    public abstract RegularFileProperty getMarker();

    @Inject
    public abstract ExecOperations getExecOperations();

    @Inject
    public abstract ProjectLayout getProjectLayout();

    @TaskAction
    public void localImport() throws IOException {
        DockerUtils dockerUtils = new DockerUtils(getExecOperations());
        final String imageId = getImageId().get();
        String uuid = null;
        if (imageExistsInDaemon(dockerUtils, imageId)) {
            getLogger().lifecycle("Docker Daemon already has image with Id {}. Skip import.", imageId);
        } else {
            final ByteArrayOutputStream out = new ByteArrayOutputStream();
            try (InputStream archiveInput = ExtractCompressedTar.uncompressedInputStream(RegularFileUtils.toPath(getImageArchive()))) {
                dockerUtils.exec(spec -> {
                    spec.setStandardInput(archiveInput);
                    spec.commandLine("docker", "load");
                    spec.setStandardOutput(out);
                });
                final String dockerLoad = out.toString().trim();
                if (dockerLoad.startsWith("Loaded image:") && dockerLoad.endsWith(":latest")) {
                    uuid = dockerLoad.substring(dockerLoad.indexOf(":") + 1);
                } else {
                    throw new GradleException("Unexpected docker load output:" + dockerLoad);
                }
            } catch (IOException e) {
                throw new GradleException("Error importing image in docker daemon", e);
            }
        }
        // The image might exist, but we want to make sure it's still tagged as we want it to
        dockerUtils.exec(spec ->
                spec.commandLine("docker", "tag", imageId, getTag().get())
        );

        String finalUuid = Objects.requireNonNull(uuid).trim();
        dockerUtils.exec(spec ->
            spec.commandLine("docker", "image", "rm", finalUuid)
        );

        getLogger().lifecycle(
                "Image tagged as {}",
                getTag().get()
        );
        Files.writeString(
                RegularFileUtils.toPath(getMarker()),
                getTag().get()
        );
    }



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


}
