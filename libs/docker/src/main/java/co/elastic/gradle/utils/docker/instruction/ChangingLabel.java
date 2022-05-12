package co.elastic.gradle.utils.docker.instruction;

import org.gradle.api.tasks.Input;

public record ChangingLabel(String key, String value) implements ContainerImageBuildInstruction {
    @Input
    public String getKey() {
        return key;
    }
}
