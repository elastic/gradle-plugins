package co.elastic.gradle.docker.base;

import co.elastic.gradle.utils.docker.DockerUtils;
import org.gradle.api.DefaultTask;
import org.gradle.api.provider.Property;
import org.gradle.api.tasks.Input;
import org.gradle.api.tasks.TaskAction;
import org.gradle.process.ExecOperations;

import javax.inject.Inject;
import java.io.IOException;
import java.util.Collections;

abstract public class DockerLocalCleanTask extends DefaultTask {

    @TaskAction
    public void cleanUpImages() throws IOException {
        DockerUtils dockerUtils = new DockerUtils(getExecOperations());
        dockerUtils.exec(spec -> {
            spec.setEnvironment(Collections.emptyMap());
            spec.commandLine("docker", "image", "rm", getImageTag().get());
            spec.setIgnoreExitValue(true);
        });
    }

    @Input
    abstract public Property<String> getImageTag();

    @Inject
    abstract public ExecOperations getExecOperations();


}
