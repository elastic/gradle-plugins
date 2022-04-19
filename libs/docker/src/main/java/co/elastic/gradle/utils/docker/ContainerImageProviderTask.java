package co.elastic.gradle.utils.docker;

import groovyjarjarantlr4.v4.runtime.misc.NotNull;
import org.gradle.api.Task;
import org.gradle.api.provider.Provider;
import org.gradle.api.tasks.Internal;

public interface ContainerImageProviderTask extends Task {

    @Internal
    @NotNull
    Provider<String> getTag();

    @Internal
    @NotNull
    Provider<String> getImageId();
}
