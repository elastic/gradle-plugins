package co.elastic.gradle.cli.base;

import org.gradle.api.DefaultTask;
import org.gradle.api.artifacts.Configuration;
import org.gradle.api.tasks.*;

import java.io.File;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Collection;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

public abstract class MultipleSymlinkTask extends DefaultTask {

    @Internal
    public Map<File, File> getNameToTargetMap() {
        final Configuration configuration = getProject().getConfigurations().getByName(BaseCliPlugin.CONFIGURATION_NAME);
        return configuration.getFiles().stream()
                .collect(Collectors.toMap(
                        value -> BaseCliPlugin.getExecutable(getProject(), value.getName()),
                        Function.identity()
                ));
    }

    @InputFiles
    public Set<File> getTarget() {
        return getNameToTargetMap().keySet();
    }

    @OutputFiles
    public Collection<File> getLinkName() {
        return getNameToTargetMap().values();
    }

    @TaskAction
    public void doLink() {
        getNameToTargetMap()
                .forEach( (linkName, target) -> {
                    getLogger().lifecycle("Linking {}", linkName);
                    final Path linkPath = linkName.toPath();
                    try {
                        if (Files.exists(linkPath)) {
                            Files.delete(linkPath);
                        }
                        if (!Files.exists(linkPath.getParent())) {
                            Files.createDirectories(linkPath.getParent());
                        }
                        Files.createSymbolicLink(linkPath, target.toPath());
                    } catch (IOException e) {
                        throw new UncheckedIOException(e);
                    }
                });
    }
}
