package co.elastic.gradle.utils.docker.instruction;

import org.gradle.api.provider.Provider;

public interface FromImageReference extends ContainerImageBuildInstruction {
    Provider<String> getReference();
}
