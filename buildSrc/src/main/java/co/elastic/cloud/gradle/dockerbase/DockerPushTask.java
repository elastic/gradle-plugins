package co.elastic.cloud.gradle.dockerbase;

import co.elastic.cloud.gradle.docker.action.JibActions;
import co.elastic.cloud.gradle.util.Architecture;
import com.google.cloud.tools.jib.api.*;
import org.gradle.api.DefaultTask;
import org.gradle.api.file.ProjectLayout;
import org.gradle.api.file.RegularFileProperty;
import org.gradle.api.provider.Property;
import org.gradle.api.tasks.*;

import javax.inject.Inject;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;

abstract public class DockerPushTask extends DefaultTask {

    @Inject
    public DockerPushTask() {
        final String baseFileName = getName() + "/" + "repo-" + Architecture.current().name().toLowerCase();
        getDigestFile().convention(
                getProjectLayout().getBuildDirectory().file(baseFileName + ".repoDigest")
        );
    }

    @OutputFile
    abstract public RegularFileProperty getDigestFile();

    @Inject
    abstract protected ProjectLayout getProjectLayout();

    @Input
    abstract protected Property<String> getTag();

    @InputFile
    abstract protected RegularFileProperty getImageArchive();

    @TaskAction
    public void pushImage() throws InvalidImageReferenceException, IOException {
        final String tag = getTag().get();
        final JibContainer container = new JibActions().pushImage(getImageArchive().get(), tag);

        final String repoDigest = container.getDigest().toString();
        Files.write(
                getDigestFile().get().getAsFile().toPath(),
                repoDigest.getBytes(StandardCharsets.UTF_8)
        );
        getLogger().lifecycle("Pushed image {}@{}", tag, repoDigest);
    }

}
