package co.elastic.gradle.dockercomponent;

import co.elastic.gradle.utils.Architecture;
import co.elastic.gradle.utils.RegularFileUtils;
import co.elastic.gradle.utils.docker.ContainerImageProviderTask;
import co.elastic.gradle.utils.docker.DockerUtils;
import co.elastic.gradle.utils.docker.instruction.ChangingLabel;
import co.elastic.gradle.utils.docker.instruction.ContainerImageBuildInstruction;
import org.gradle.api.DefaultTask;
import org.gradle.api.file.ProjectLayout;
import org.gradle.api.file.RegularFileProperty;
import org.gradle.api.provider.MapProperty;
import org.gradle.api.provider.Property;
import org.gradle.api.provider.Provider;
import org.gradle.api.tasks.*;
import org.gradle.process.ExecOperations;

import javax.inject.Inject;
import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.util.List;
import java.util.stream.Collectors;


abstract public class DockerComponentLocalImport extends DefaultTask implements ContainerImageProviderTask {

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
                        .filter(jibInstruction -> ! (jibInstruction instanceof ChangingLabel))
                        .collect(Collectors.toList()),
                getProject().getBuildDir().toPath().resolve("dockerComponentImageBuild")
        );
        Files.writeString(getMarker().toPath(), getTag().get());
        getLogger().lifecycle("Image with Id {} tagged as {}",
                RegularFileUtils.readString(getImageIdFile().get()).trim(),
                getTag().get()
        );
        if (getLogger().isInfoEnabled()) {
            (new DockerUtils(getExecOperations())).exec(execSpec -> {
                execSpec.commandLine("docker", "inspect", getTag().get());
            });
        }
    }

    @OutputFile
    public File getMarker() {
        return new File(getProject().getBuildDir(), "docker_local_images/" + getName());
    }

    @Nested
    abstract MapProperty<Architecture, List<ContainerImageBuildInstruction>> getInstructions();

    @Input
    @Override
    abstract public Property<String> getTag();

    @Internal
    public Provider<String> getImageId() {
        //Convenience Provider to access the imageID from the imageIdFile
        return getImageIdFile().map(RegularFileUtils::readString).map(String::trim);
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
