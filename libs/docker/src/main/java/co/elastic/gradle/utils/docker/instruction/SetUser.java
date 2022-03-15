package co.elastic.gradle.utils.docker.instruction;

import org.gradle.api.tasks.Input;

public class SetUser implements ContainerImageBuildInstruction {
    private final String username;

    public SetUser(String username) {
        this.username = username;
    }

    @Input
    public String getUsername() {
        return username;
    }
}

