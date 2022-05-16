package co.elastic.gradle.utils.docker.instruction;

import org.gradle.api.tasks.Input;

import java.util.List;

public record Cmd(List<String> value) implements ContainerImageBuildInstruction {
    @Input
    public List<String> getValue() {
        return value;
    }
}
