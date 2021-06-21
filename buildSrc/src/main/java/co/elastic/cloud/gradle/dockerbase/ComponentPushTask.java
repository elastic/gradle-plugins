package co.elastic.cloud.gradle.dockerbase;

import co.elastic.cloud.gradle.docker.DockerPluginConventions;
import co.elastic.cloud.gradle.docker.build.DockerBuildInfo;
import org.gradle.api.DefaultTask;
import org.gradle.api.provider.Provider;
import org.gradle.api.tasks.Internal;
import org.gradle.api.tasks.TaskAction;

import javax.inject.Inject;

public class ComponentPushTask extends DefaultTask {

    private Provider<ComponentBuildTask> buildTask;
    private DockerBuildInfo buildInfo;

    @Inject
    public ComponentPushTask(Provider<ComponentBuildTask> buildTask) {
        super();
        this.buildTask = buildTask;
    }

    @TaskAction
    public void pushImage() {
        this.buildInfo = buildTask.get().push();
    }

    @Internal
    public DockerBuildInfo getBuildInfo() {
        return buildInfo;
    }
}
