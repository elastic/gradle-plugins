package co.elastic.gradle.dockerbase;

import co.elastic.gradle.utils.Architecture;
import co.elastic.gradle.utils.RegularFileUtils;
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
    public abstract RegularFileProperty getDigestFile();

    @Inject
    abstract protected ProjectLayout getProjectLayout();

    @Input
    public abstract Property<String> getTag();

    @Input
    public abstract Property<Instant> getCreatedAt();

    @InputFile
    @PathSensitive(PathSensitivity.NONE)
    public abstract RegularFileProperty getImageArchive();

    @TaskAction
    public void pushImage() throws IOException {
        final String tag = getTag().get();
        final Instant createdAt = getCreatedAt().get();
        final JibContainer container = new JibPushActions().pushImage(
                RegularFileUtils.toPath(getImageArchive()),
                tag,
                createdAt
        );

        final String repoDigest = container.getDigest().toString();
        Files.writeString(
                RegularFileUtils.toPath(getDigestFile()),
                repoDigest
        );
        getLogger().lifecycle("Pushed image {}@{}", tag, repoDigest);
    }
}
