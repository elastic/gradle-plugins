package co.elastic.gradle.utils.docker.instruction;

import org.gradle.api.tasks.Input;

public record Maintainer(String name, String email) implements ContainerImageBuildInstruction {

    @Input
    public String getName() {
        return name;
    }

    @Input
    public String getEmail() {
        return email;
    }
}
