package co.elastic.gradle.utils.docker.instruction;

import org.gradle.api.tasks.Input;

public record Label(String key, String value) implements ContainerImageBuildInstruction {

    @Input
    public String getKey() {
        return key;
    }

    @Input
    public String getValue() {
        return value;
    }
}
