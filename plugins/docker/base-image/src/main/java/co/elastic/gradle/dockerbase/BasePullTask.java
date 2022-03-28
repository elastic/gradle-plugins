package co.elastic.gradle.dockerbase;

import co.elastic.gradle.utils.docker.DockerUtils;
import co.elastic.gradle.utils.docker.instruction.From;
import org.gradle.api.DefaultTask;
import org.gradle.api.tasks.TaskAction;
import org.gradle.api.tasks.TaskProvider;
import org.gradle.process.ExecOperations;

import javax.inject.Inject;

public class BasePullTask extends DefaultTask {

    private final  TaskProvider<BaseBuildTask> buildTask;

    @Inject
    public BasePullTask( TaskProvider<BaseBuildTask> buildTask) {
        this.buildTask = buildTask;
    }

    @TaskAction
    public void pull() {
        DockerUtils dockerUtils = new DockerUtils(getExecOperations());
        buildTask.get().getInstructions().stream()
                .filter(it -> it instanceof From)
                .map(it -> ((From) it))
                .findAny()
                .ifPresent(it ->
                        dockerUtils.pull(it.getImage() + ":" + it.getVersion() + "@" + it.getSha())
                );
    }

    @Inject
    public ExecOperations getExecOperations() {
        throw new IllegalStateException("not implemented");
    }

}
