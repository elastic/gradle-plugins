package co.elastic.gradle.utils.docker.instruction;

public interface FromImageReference extends ContainerImageBuildInstruction {
    String getReference();
}
