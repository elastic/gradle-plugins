package co.elastic.gradle.utils.docker.instruction;

import org.gradle.api.tasks.Input;
import org.gradle.api.tasks.Internal;

public class ChangingLabel implements ContainerImageBuildInstruction {
    private final String key;
    private String value;

    public ChangingLabel(String key, String value) {
        this.key = key;
        this.value = value;
    }

    @Input
    public String getKey() {
        return key;
    }

    @Internal
    public String getValue() {
        return value;
    }

    public ChangingLabel setValue(String value) {
        this.value = value;
        return this;
    }
}
