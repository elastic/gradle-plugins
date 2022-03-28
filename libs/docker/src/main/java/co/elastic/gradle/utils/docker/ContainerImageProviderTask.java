package co.elastic.gradle.utils.docker;

import org.gradle.api.Task;
import org.gradle.api.provider.Provider;
import org.gradle.api.tasks.Internal;

public interface ContainerImageProviderTask extends Task {

    @Internal
    Provider<String> getTag();

    @Internal
    Provider<String> getImageId();
}
