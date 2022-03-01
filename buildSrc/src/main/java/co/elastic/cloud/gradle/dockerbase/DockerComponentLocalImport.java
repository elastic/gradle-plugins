package co.elastic.cloud.gradle.dockerbase;

import co.elastic.cloud.gradle.docker.DockerPluginConventions;
import co.elastic.cloud.gradle.docker.action.DockerDaemonActions;
import co.elastic.cloud.gradle.docker.action.JibActions;
import co.elastic.cloud.gradle.util.Architecture;
import co.elastic.cloud.gradle.util.FileUtils;
import org.gradle.api.DefaultTask;
import org.gradle.api.file.ProjectLayout;
import org.gradle.api.file.RegularFileProperty;
import org.gradle.api.provider.MapProperty;
import org.gradle.api.provider.Property;
import org.gradle.api.provider.Provider;
import org.gradle.api.tasks.*;
import org.gradle.process.ExecOperations;

import javax.inject.Inject;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.List;
import java.util.stream.Collectors;


abstract public class DockerComponentLocalImport extends DefaultTask implements DockerLocalImportTask {

    public DockerComponentLocalImport() {
        getImageIdFile().convention(
                getProjectLayout()
                        .getBuildDirectory()
                        .file(getName() + "/" + "image-local.imageId")
                        .get()
        );

    }

    @TaskAction
    public void localImport() throws IOException {
        JibActions actions = new JibActions();
        actions.buildTo(
                getTag().get(),
                getImageIdFile().get(),
                getInstructions().get().get(Architecture.current())
                        .stream()
                        .filter(jibInstruction -> ! (jibInstruction instanceof JibInstruction.ChangingLabel))
                        .collect(Collectors.toList()),
                getProject().getBuildDir().toPath().resolve("dockerComponentImageBuild")
        );
        Files.write(getMarker().toPath(), getTag().get().getBytes(StandardCharsets.UTF_8));
        getLogger().lifecycle("Image with Id {} tagged as {}",
                FileUtils.readFromRegularFile(getImageIdFile().get()),
                getTag().get()
        );
        if (getLogger().isInfoEnabled()) {
            (new DockerDaemonActions(getExecOperations())).execute(execSpec -> {
                execSpec.commandLine("docker", "inspect", getTag().get());
            });
        }
    }

    @Nested
    abstract MapProperty<Architecture, List<JibInstruction>> getInstructions();

    @Input
    abstract public Property<String> getTag();

    @Internal
    @Override
    public Provider<String> getImageId() {
        //Convenience Provider to access the imageID from the imageIdFile
        return getImageIdFile().map(FileUtils::readFromRegularFile);
    }

    @OutputFile
    abstract public RegularFileProperty getImageIdFile();

    @Inject
    abstract protected ProjectLayout getProjectLayout();

    @Inject
    public ExecOperations getExecOperations() {
        throw new IllegalStateException("not implemented");
    }
}
