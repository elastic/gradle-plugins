package co.elastic.gradle.dockerbase;

import co.elastic.gradle.utils.Architecture;
import com.google.cloud.tools.jib.api.InvalidImageReferenceException;
import com.google.cloud.tools.jib.api.JibContainer;
import org.gradle.api.DefaultTask;
import org.gradle.api.file.ProjectLayout;
import org.gradle.api.file.RegularFileProperty;
import org.gradle.api.provider.Property;
import org.gradle.api.tasks.*;

import javax.inject.Inject;
import java.io.IOException;
import java.nio.file.Files;
import java.time.Instant;

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

    @Input
    abstract protected Property<Instant> getCreatedAt();

    @InputFile
    @PathSensitive(PathSensitivity.NONE)
    abstract protected RegularFileProperty getImageArchive();

    @TaskAction
    public void pushImage() throws InvalidImageReferenceException, IOException {
        final String tag = getTag().get();
        final Instant createdAt = getCreatedAt().get();
        // FIXME: Don't depend on Jib here
        final JibContainer container = new JibPushActions().pushImage(
            getImageArchive().get().getAsFile().toPath(), 
            tag, 
            createdAt
        );

        final String repoDigest = container.getDigest().toString();
        Files.writeString(
                getDigestFile().get().getAsFile().toPath(),
                repoDigest
        );
        getLogger().lifecycle("Pushed image {}@{}", tag, repoDigest);
    }
}
