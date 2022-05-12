package co.elastic.gradle.utils.docker.instruction;

import org.gradle.api.tasks.Input;

import java.util.List;

public record Install(List<String> packages) implements ContainerImageBuildInstruction {

    @Input
    public List<String> getPackages() {
        return List.copyOf(packages);
    }

}

