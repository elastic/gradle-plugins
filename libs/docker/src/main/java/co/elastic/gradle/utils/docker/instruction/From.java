package co.elastic.gradle.utils.docker.instruction;

import org.gradle.api.tasks.Input;
import org.gradle.api.tasks.Optional;

public class From implements ContainerImageBuildInstruction {

    private final String image;
    private final String version;
    private final String sha;

    public From(String image, String version, String sha) {
        this.image = image;
        this.version = version;
        this.sha = sha;
    }

    @Input
    public String getImage() {
        return image;
    }

    @Input
    public String getVersion() {
        return version;
    }

    @Input
    @Optional
    public String getSha() {
        return sha;
    }
}
