package co.elastic.cloud.gradle.docker;

import org.gradle.api.tasks.CacheableTask;
import org.gradle.api.tasks.Nested;

@CacheableTask
public class DockerBuildTask extends org.gradle.api.DefaultTask {

    private DockerFileExtension extension;

    @Nested
    public DockerFileExtension getExtension() {
        return extension;
    }

    public void setExtension(DockerFileExtension extension) {
        this.extension = extension;
    }
}
