package co.elastic.gradle.utils.docker.instruction;

import org.gradle.api.tasks.Input;

public record SetUser(String username) implements ContainerImageBuildInstruction {

    @Input
    public String getUsername() {
        return username;
    }
}

