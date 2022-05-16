package co.elastic.gradle.utils.docker;

import org.gradle.api.tasks.Input;

import java.io.Serializable;
import java.util.Objects;

public record UnchangingContainerReference(
        String repository,
        String tag,
        String digest
) implements Serializable {

    public UnchangingContainerReference(String repository, String tag, String digest) {
        this.repository = Objects.requireNonNull(repository);
        this.tag = Objects.requireNonNull(tag);
        this.digest = Objects.requireNonNull(digest);
    }

    @Input
    public String getRepository() {
        return repository;
    }

    @Input
    public String getTag() {
        return tag;
    }

    @Input
    public String getDigest() {
        return digest;
    }
}
