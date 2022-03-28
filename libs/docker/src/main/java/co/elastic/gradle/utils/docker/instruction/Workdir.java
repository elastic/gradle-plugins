package co.elastic.gradle.utils.docker.instruction;

import org.gradle.api.tasks.Input;

public class Workdir implements ContainerImageBuildInstruction {
    private final String folder;

    public Workdir(String folder) {
        this.folder = folder;
    }

    @Input
    public String getFolder() {
        return folder;
    }
}
