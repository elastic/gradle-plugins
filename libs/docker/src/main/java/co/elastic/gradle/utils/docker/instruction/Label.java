package co.elastic.gradle.utils.docker.instruction;

import org.gradle.api.tasks.Input;

public class Label implements ContainerImageBuildInstruction {
    private final String key;
    private final String value;

    public Label(String key, String value) {
        this.key = key;
        this.value = value;
    }

    @Input
    public String getKey() {
        return key;
    }

    @Input
    public String getValue() {
        return value;
    }
}
