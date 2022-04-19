package co.elastic.gradle.utils.docker.instruction;

import org.gradle.api.tasks.Input;

import java.util.List;

public class Install implements ContainerImageBuildInstruction {
    private final List<String> packages;

    public Install(List<String> packages) {
        this.packages = packages;
    }

    @Input
    public List<String> getPackages() {
        return List.copyOf(packages);
    }

}

