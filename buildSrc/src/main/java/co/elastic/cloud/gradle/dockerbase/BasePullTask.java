package co.elastic.cloud.gradle.dockerbase;

import co.elastic.cloud.gradle.docker.action.DockerDaemonActions;
import org.gradle.api.DefaultTask;
import org.gradle.api.tasks.TaskAction;
import org.gradle.api.tasks.TaskProvider;
import org.gradle.process.ExecOperations;

import javax.inject.Inject;
import java.util.Optional;

public class BasePullTask extends DefaultTask {

    private final  TaskProvider<BaseBuildTask> buildTask;

    @Inject
    public BasePullTask( TaskProvider<BaseBuildTask> buildTask) {
        this.buildTask = buildTask;
    }

    @TaskAction
    public void pull() {
        DockerDaemonActions daemonActions = new DockerDaemonActions(getExecOperations());
        buildTask.get().getInstructions().stream()
                .filter(it -> it instanceof DaemonInstruction.From)
                .map(it -> ((DaemonInstruction.From) it))
                .findAny()
                .ifPresent(it ->
                        daemonActions.pull(it.getImage() + ":" + it.getVersion() + "@" + it.getSha())
                );
    }

    @Inject
    public ExecOperations getExecOperations() {
        throw new IllegalStateException("not implemented");
    }

}
