package co.elastic.gradle.license_headers;

import org.gradle.api.DefaultTask;
import org.gradle.api.GradleException;
import org.gradle.api.file.ProjectLayout;
import org.gradle.api.file.RegularFileProperty;
import org.gradle.api.provider.ListProperty;
import org.gradle.api.tasks.*;

import javax.inject.Inject;
import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@CacheableTask
public abstract class CheckLicenseHeadersTask extends DefaultTask {

    public CheckLicenseHeadersTask() {
        getMarkerFile().convention(
                getProjectLayout().getBuildDirectory().file(getName() + ".marker")
        );
    }

    @Nested
    public abstract ListProperty<LicenseHeaderConfig> getConfigs();

    @OutputFile
    public abstract RegularFileProperty getMarkerFile();

    @TaskAction
    public void doCheck() throws IOException {
        if (getConfigs().get().isEmpty()) {
            throw new GradleException("No license header configurations defined, use `licenseHeaders { checkAll() }` or  `licenseHeaders { matching(...) { }}`");
        }
        for (LicenseHeaderConfig c : getConfigs().get()) {
            final String[] expectedHeader = Files.readAllLines(c.getHeaderFile().get().getAsFile().toPath()).toArray(String[]::new);
            Path projectDir = getProjectLayout().getProjectDirectory().getAsFile().toPath();
            final List<File> files = c.getFiles().get();

            files.forEach(f -> getLogger().info("Checking {}", f));
            getLogger().lifecycle("Scanning {} files for headers", files.size());
            if (files.isEmpty()) {
                throw new GradleException("Nothing to scan");
            }

            final Map<Path, ViolationReason> brokenFiles = LicenseCheckUtils.nonCompliantFilesWithReason(projectDir, expectedHeader, files);

            if (!brokenFiles.isEmpty()) {
                throw new GradleException("Incorrect header:\n" +
                                          brokenFiles.entrySet().stream()
                                                  .map(entry -> projectDir.relativize(entry.getKey()) + " : " + entry.getValue().reason())
                                                  .collect(Collectors.joining("\n"))
                );
            }
        }
        Files.writeString(getMarkerFile().get().getAsFile().toPath(), "Header is present in all files");
    }

    @Inject
    protected abstract ProjectLayout getProjectLayout();


}
