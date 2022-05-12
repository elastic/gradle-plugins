package co.elastic.gradle.sandbox;

import co.elastic.gradle.utils.RegularFileUtils;
import co.elastic.gradle.utils.docker.DockerUtils;
import org.gradle.api.DefaultTask;
import org.gradle.api.file.ProjectLayout;
import org.gradle.api.file.RegularFileProperty;
import org.gradle.api.provider.ListProperty;
import org.gradle.api.tasks.Input;
import org.gradle.api.tasks.OutputFile;
import org.gradle.api.tasks.TaskAction;
import org.gradle.process.ExecOperations;

import javax.inject.Inject;
import java.io.IOException;
import java.nio.file.Files;

public abstract class DockerImagePull extends DefaultTask {

    public DockerImagePull() {
        getMarkerFile().convention(
            getProjectLayout().getBuildDirectory().file("sandbox/" + getName() + ".marker")
        );
    }

    @Input
    abstract ListProperty<String> getTags();

    @Inject
    protected abstract ExecOperations getExecOperations();

    @Inject
    protected abstract ProjectLayout getProjectLayout();

    @OutputFile
    abstract RegularFileProperty getMarkerFile();

    @TaskAction
    public void pullImages() throws IOException {
        final DockerUtils dockerUtils = new DockerUtils(getExecOperations());
        getTags().get().forEach(tag -> {
            getLogger().lifecycle("Pulling docker image: {}", tag);
            dockerUtils.pull(tag);
        });
        Files.writeString(
                RegularFileUtils.toPath(getMarkerFile()),
                "Pulled the following tags:\n" + String.join("\n", getTags().get())
        );
    }

}
