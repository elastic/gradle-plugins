package co.elastic.gradle;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

public class GradleTestkitHelper {

    private final Path projectDir;
    private final Path settingsFile;
    private final Path buildFile;

    public GradleTestkitHelper(Path projectDir) {
        this.projectDir = projectDir;
        settingsFile = projectDir.resolve("settings.gradle.kts");
        buildFile = projectDir.resolve("build.gradle.kts");
    }

    public void settings(String content) {
        System.out.println("settings.gradle.kts:");
        System.out.println(content);
        try {
            Files.writeString(settingsFile, content, StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    public void buildScript(String content) {
        try {
            Files.writeString(buildFile, content, StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    public Path getSettingsFile() {
        return settingsFile;
    }

    public Path getBuildFile() {
        return buildFile;
    }
}
