package co.elastic.gradle.utils.docker.instruction;

import org.gradle.api.tasks.Input;

import java.util.List;

public class Cmd implements ContainerImageBuildInstruction {

    private final List<String> value;

    public Cmd(List<String> value) {
        this.value = value;
    }

    @Input
    public List<String> getValue() {
        return value;
    }
}
