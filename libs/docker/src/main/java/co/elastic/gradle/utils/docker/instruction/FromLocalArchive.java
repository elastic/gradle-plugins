package co.elastic.gradle.utils.docker.instruction;

import org.gradle.api.provider.Provider;
import org.gradle.api.tasks.Input;

import java.io.File;

public record FromLocalArchive(
        Provider<File> archive,
        // No need to declare as input as we have the ID, so we can save some time by avoiding fingerprinting large files
        Provider<String> imageId
) implements ContainerImageBuildInstruction {

    @Input
    public Provider<String> getImageId() {
        return imageId;
    }

}
