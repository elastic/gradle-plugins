package co.elastic.gradle.dockerbase;

import co.elastic.gradle.utils.RegularFileUtils;
import co.elastic.gradle.utils.docker.DockerUtils;
import org.gradle.api.DefaultTask;
import org.gradle.api.file.ProjectLayout;
import org.gradle.api.file.RegularFileProperty;
import org.gradle.api.provider.Property;
import org.gradle.api.tasks.Input;
import org.gradle.api.tasks.OutputFile;
import org.gradle.api.tasks.TaskAction;
import org.gradle.process.ExecOperations;

import javax.inject.Inject;
import java.io.IOException;
import java.nio.file.Files;

public abstract class BasePullTask extends DefaultTask {

    public BasePullTask() {
        getMarkerFile().convention(
                getProjectLayout().getBuildDirectory().file(getName() + ".marker")
        );
    }

    @Input
    public abstract Property<String> getTag();

    @OutputFile
    public abstract RegularFileProperty getMarkerFile();

    @TaskAction
    public void pull() throws IOException {
        DockerUtils dockerUtils = new DockerUtils(getExecOperations());
        final String tag = getTag().get();
        dockerUtils.pull(tag);
        Files.writeString(
                RegularFileUtils.toPath(getMarkerFile()),
                tag
        );
    }

    @Inject
    protected abstract ExecOperations getExecOperations();

    @Inject
    protected abstract ProjectLayout getProjectLayout();

}
