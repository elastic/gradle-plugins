package co.elastic.gradle.utils.docker.instruction;

import org.gradle.api.tasks.Input;
import org.gradle.api.tasks.Internal;
import org.gradle.api.tasks.Optional;

import java.util.Objects;

public class From implements FromImageReference {

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

    @Internal
    @Override
    public String getReference() {
        Objects.requireNonNull(image);
        if (sha == null) {
            if (version == null) {
                return image;
            } else {
                return String.format("%s:%s", image, version);
            }
        } else {
            if (version == null) {
                return String.format("%s@%s", image, sha);
            } else {
                return String.format("%s:%s@%s", image, version, sha);
            }
        }
    }
}
