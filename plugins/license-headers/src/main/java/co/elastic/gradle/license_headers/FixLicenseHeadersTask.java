package co.elastic.gradle.license_headers;

import org.gradle.api.DefaultTask;
import org.gradle.api.file.ProjectLayout;
import org.gradle.api.provider.ListProperty;
import org.gradle.api.tasks.Nested;
import org.gradle.api.tasks.TaskAction;

import javax.inject.Inject;
import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;

public abstract class FixLicenseHeadersTask extends DefaultTask {

    @Nested
    public abstract ListProperty<LicenseHeaderConfig> getConfigs();

    @TaskAction
    public void fixHeaders() throws IOException {
        for (LicenseHeaderConfig c : getConfigs().get()) {
            Path projectDir = getProjectLayout().getProjectDirectory().getAsFile().toPath();
            final String[] expectedHeader = Files.readAllLines(c.getHeaderFile().get().getAsFile().toPath()).toArray(String[]::new);
            final List<File> files = c.getFiles().get();
            final Map<Path, ViolationReason> brokenPaths = LicenseCheckUtils.nonCompliantFilesWithReason(
                    projectDir, expectedHeader, files
            );
            for (Map.Entry<Path, ViolationReason> entry : brokenPaths.entrySet()) {
                if (entry.getValue().type().equals(ViolationReason.Type.LINE_MISS_MATCH)) {
                    getLogger().warn("Can't automatically fix `{}` : {}", projectDir.relativize(entry.getKey()), entry.getValue().reason());
                } else {
                    Files.write(
                            entry.getKey(),
                            (Iterable<String>) Stream.concat(
                                    Arrays.stream(expectedHeader),
                                    Files.lines(entry.getKey())
                            )::iterator
                    );
                    getLogger().lifecycle("Added header to {}", projectDir.relativize(entry.getKey()));
                }
            }
        }
    }


    @Inject
    protected abstract ProjectLayout getProjectLayout();
}

