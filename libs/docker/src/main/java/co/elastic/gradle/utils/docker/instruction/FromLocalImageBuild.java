package co.elastic.gradle.utils.docker.instruction;

import org.gradle.api.provider.Provider;
import org.gradle.api.tasks.Input;
import org.gradle.api.tasks.Internal;

public record FromLocalImageBuild(String otherProjectPath,
                                  Provider<String> tag,
                                  Provider<String> imageId)
        implements FromImageReference {

    @Input
    public Provider<String> getImageId() {
        return imageId;
    }

    @Override
    @Internal
    public Provider<String> getReference() {
        return tag;
    }
}