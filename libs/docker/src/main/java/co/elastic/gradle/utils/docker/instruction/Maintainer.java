package co.elastic.gradle.utils.docker.instruction;

import org.gradle.api.tasks.Input;

public class Maintainer implements ContainerImageBuildInstruction {

    private final String name;
    private final String email;

    public Maintainer(String name, String email) {
        this.name = name;
        this.email = email;
    }

    @Input
    public String getName() {
        return name;
    }

    @Input
    public String getEmail() {
        return email;
    }
}
