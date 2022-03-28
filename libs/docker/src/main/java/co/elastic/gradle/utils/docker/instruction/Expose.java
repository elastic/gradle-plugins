package co.elastic.gradle.utils.docker.instruction;

import org.gradle.api.tasks.Input;
import java.io.Serializable;

public class Expose implements ContainerImageBuildInstruction {
    private final Type type;
    private final Integer port;

    public Expose(Type type, Integer port) {
        this.type = type;
        this.port = port;
    }

    @Input
    public Type getType() {
        return type;
    }

    @Input
    public Integer getPort() {
        return port;
    }

    public enum Type implements Serializable {
        TCP, UDP
    }
}
