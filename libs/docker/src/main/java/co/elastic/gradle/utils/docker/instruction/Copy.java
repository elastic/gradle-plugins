package co.elastic.gradle.utils.docker.instruction;

import org.gradle.api.Action;
import org.gradle.api.file.CopySpec;
import org.gradle.api.tasks.Input;
import org.gradle.api.tasks.Internal;
import org.gradle.api.tasks.Optional;

public class Copy implements ContainerImageBuildInstruction {
    private final Action<CopySpec> spec;
    private final String layer;
    private final String owner;

    public Copy(Action<CopySpec> spec, String layer, String owner) {
        this.spec = spec;
        this.layer = layer;
        this.owner = owner;
    }

    // The CopySpec can't be an input on its own.
    // We make sure to register the files that the copy spec covers in the task at runtime
    @Internal
    public Action<CopySpec> getSpec() {
        return spec;
    }

    @Internal
    public String getLayer() {
        return layer;
    }

    @Input
    @Optional
    public String getOwner() {
        return owner;
    }
}
