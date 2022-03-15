package co.elastic.gradle.utils.docker.instruction;

import org.gradle.api.provider.Provider;
import org.gradle.api.tasks.Input;
import org.gradle.api.tasks.Internal;

public class FromLocalImageBuild implements ContainerImageBuildInstruction {

    private final Provider<String> tag;
    private final Provider<String> imageId;
    private final String otherProjectPath;

    public FromLocalImageBuild(String otherProjectPath, Provider<String> tag, Provider<String> imageId) {
        this.tag = tag;
        this.imageId = imageId;
        this.otherProjectPath = otherProjectPath;
    }

    @Input
    public Provider<String> getTag() {
        return tag;
    }

    @Input
    public Provider<String> getImageId() {
        return imageId;
    }

    @Internal
    public String getOtherProjectPath() {
        return otherProjectPath;
    }
}