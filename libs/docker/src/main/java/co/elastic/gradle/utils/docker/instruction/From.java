package co.elastic.gradle.utils.docker.instruction;

import org.gradle.api.provider.Provider;
import org.gradle.api.tasks.Input;

public record From(Provider<String> tag) implements FromImageReference {

    @Input
    @Override
    public Provider<String> getReference() {
        return tag;
    }

}
