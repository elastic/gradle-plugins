package co.elastic.gradle.utils.docker.instruction;

import org.gradle.api.tasks.Input;

public record Workdir(String folder) implements ContainerImageBuildInstruction {

    @Input
    public String getFolder() {
        return folder;
    }
}
