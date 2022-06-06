package co.elastic.gradle.utils.docker.instruction;

import org.gradle.api.tasks.Input;

import java.util.List;

public record RepoConfigRun(List<String> commands) implements ContainerImageBuildInstruction {
    @Input
    public List<String> getCommands() {
        return commands;
    }
}
