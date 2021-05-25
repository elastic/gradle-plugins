package co.elastic.cloud.gradle.dockerbase;

import co.elastic.cloud.gradle.docker.build.DockerBuildInfo;
import org.gradle.api.DefaultTask;
import org.gradle.api.provider.Provider;
import org.gradle.api.tasks.TaskAction;

import javax.inject.Inject;

public class ComponentPushTask extends DefaultTask {

    private Provider<ComponentBuildTask> buildTask;
    private final String pushTag;

    @Inject
    public ComponentPushTask(Provider<ComponentBuildTask> buildTask, String pushTag) {
        this.buildTask = buildTask;
        this.pushTag = pushTag;
    }

    @TaskAction
    public void pushImage() {
        DockerBuildInfo buildInfo = buildTask.get().getBuildContext().loadImageBuildInfo();
        String imageTag = buildInfo.getTag();
        if (!imageTag.equals(pushTag)) {
            getLogger().info("Pushing {} as {}", imageTag, pushTag);
        } else {
            getLogger().lifecycle("Pushing image {}", pushTag);
        }
        buildTask.get().push(pushTag);
    }
}
