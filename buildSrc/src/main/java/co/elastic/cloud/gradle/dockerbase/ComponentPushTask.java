package co.elastic.cloud.gradle.dockerbase;


import co.elastic.cloud.gradle.docker.action.JibActions;
import co.elastic.cloud.gradle.util.Architecture;
import com.google.cloud.tools.jib.api.JibContainer;
import org.gradle.api.DefaultTask;
import org.gradle.api.GradleException;
import org.gradle.api.file.ProjectLayout;
import org.gradle.api.file.RegularFile;
import org.gradle.api.provider.MapProperty;
import org.gradle.api.provider.Provider;
import org.gradle.api.tasks.*;

import javax.inject.Inject;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.Collection;
import java.util.Map;
import java.util.stream.Collectors;

abstract public class ComponentPushTask extends DefaultTask {

    public ComponentPushTask() {
        getDigestFiles().convention(
               getImageArchive().map(map -> map.entrySet().stream()
                           .collect(Collectors.toMap(
                                   Map.Entry::getKey,
                                   entry -> getProjectLayout().getBuildDirectory().file(entry.getValue().getAsFile().getName() + ".repoDigest")
                           )))
        );
    }

    @Internal
    abstract public MapProperty<Architecture, Provider<RegularFile>> getDigestFiles();

    @Internal
    abstract public MapProperty<Architecture, RegularFile> getImageArchive();

    @InputFiles
    public Collection<RegularFile> getAllImageArchives() {
        return getImageArchive().get().values();
    }

    @OutputFiles
    public Collection<Provider<RegularFile>> getAllDigestFiles() {
        return getDigestFiles().get().values();
    }


    @Internal
    public Provider<Map<Architecture, String>> getDigests() {
        return getDigestFiles().map(idFiles -> idFiles.entrySet()
                .stream()
                .collect(Collectors.toMap(Map.Entry::getKey, entry -> {
                    try {
                        return Files.readAllLines(entry.getValue().get().getAsFile().toPath()).get(0);
                    } catch (IOException e) {
                        throw new GradleException("Internal error: Can't read Iamge ID file: " + entry.getValue(), e);
                    }
                })));
    }

    @Inject
    abstract protected ProjectLayout getProjectLayout();

    @Input
    abstract public MapProperty<Architecture, String> getTags();

    @TaskAction
    public void pushImage() {
        final JibActions jibActions = new JibActions();
        getImageArchive().get().forEach((architecture, imageArchive) -> {
            final String tag = getTags().get().get(architecture);
            final JibContainer container = jibActions.pushImage(imageArchive, tag);
            final String repoDigest = container.getDigest().toString();
            try {
                Files.write(
                        getDigestFiles().get().get(architecture).get().getAsFile().toPath(),
                        repoDigest.getBytes(StandardCharsets.UTF_8)
                );
            } catch (IOException e) {
                throw new UncheckedIOException(e);
            }
            getLogger().lifecycle("Pushed image {}@{}", tag, repoDigest);
        });
    }


}



