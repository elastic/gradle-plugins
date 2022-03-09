package co.elastic.gradle;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

public record GradleTestkitHelper(Path projectDir) {

    public void settings(String content) {
        writeFile(projectDir.resolve("settings.gradle.kts"), content);
    }

    public void buildScript(String content) {
        writeFile(projectDir.resolve("build.gradle.kts"), content);
    }

    public void buildScript(String subprojectPath, String content) {
        writeFile(projectDir.resolve(subprojectPath).resolve("build.gradle.kts"), content);
    }

    private void writeFile(Path path, String content) {
        final Path relativePath = projectDir.relativize(path);
        System.out.println(relativePath);
        System.out.println("-".repeat(relativePath.toString().length()));
        final List<String> lines = content.lines().toList();
        for (int i = 1; i <= lines.size(); i++) {
            System.out.println(String.format("%1$2s:", i) + lines.get(i - 1));
        }
        System.out.println();
        try {
            Files.createDirectories(path.getParent());
            Files.writeString(path, content, StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }
}
