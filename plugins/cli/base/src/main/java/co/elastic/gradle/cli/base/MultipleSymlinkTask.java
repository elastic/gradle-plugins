package co.elastic.gradle.cli.base;

import co.elastic.gradle.utils.Architecture;
import co.elastic.gradle.utils.OS;
import org.gradle.api.DefaultTask;
import org.gradle.api.GradleException;
import org.gradle.api.artifacts.Configuration;
import org.gradle.api.artifacts.Dependency;
import org.gradle.api.tasks.InputFiles;
import org.gradle.api.tasks.Internal;
import org.gradle.api.tasks.OutputFiles;
import org.gradle.api.tasks.TaskAction;

import java.io.File;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;
import java.util.function.Function;
import java.util.stream.Collectors;

public abstract class MultipleSymlinkTask extends DefaultTask {

    @Internal
    public Map<File, File> getNameToTargetMap() {
        final Configuration configuration = getProject().getConfigurations().getByName(BaseCliPlugin.CONFIGURATION_NAME);
        final Set<String> allVersions = configuration.getDependencies().stream()
                .map(Dependency::getVersion)
                .collect(Collectors.toSet());

        final Map<File, File> result = new HashMap<>(configuration.getFiles().stream()
                .collect(Collectors.toMap(
                        value -> BaseCliPlugin.getExecutable(
                                getProject(),
                                normalizeName(value.getName(), allVersions)
                        ),
                        Function.identity()
                ))
        );


        final Map<File, File> currentArch = getDefaultSymlink(configuration, Architecture.current(), allVersions);
        if (!currentArch.isEmpty()) {
            result.putAll(currentArch);
            return result;
        } else {
            if (OS.current().equals(OS.DARWIN)) {
                // Some tools don't (yet) support ARM on MacOS but work with emulation
                final Map<File, File> alternative = getDefaultSymlink(configuration, Architecture.X86_64, allVersions);
                if (!alternative.isEmpty()) {
                    result.putAll(alternative);
                    return result;
                }
            }
        }
        throw new GradleException("Could not find the architecture specific binary from " + configuration.getFiles());
    }

    private String normalizeName(String targetName, Set<String> allVersions) {
        for (String version : allVersions) {
            targetName = targetName.replace("-" + version, "")
                    .replace("-v" + version, "")
                    .replace(version, "");
        }
        for (Architecture value : Architecture.values()) {
            targetName = targetName.replace(value.dockerName(), value.name().toLowerCase(Locale.ROOT));
        }
        return targetName;
    }

    private Map<File, File> getDefaultSymlink(Configuration configuration, Architecture arch, final Set<String> allVersions) {
        return configuration.getFiles().stream()
                .filter(value ->
                        value.getName().toLowerCase(Locale.ROOT)
                                .contains(arch.name().toLowerCase(Locale.ROOT)) ||
                        value.getName().toLowerCase(Locale.ROOT)
                                .contains(arch.dockerName().toLowerCase(Locale.ROOT)) ||
                        value.getName().contains(OS.current().map(
                                Map.of(OS.DARWIN, "macos")
                        ))
                )
                .filter(value -> value.getName().contains(OS.current().name()) ||
                                 value.getName().contains(OS.current().name().toLowerCase(Locale.ROOT)) ||
                                 value.getName().contains(OS.current().map(
                                         Map.of(OS.DARWIN, "macos")
                                 ))
                )
                .collect(Collectors.toMap(
                        value -> {
                            String name = value.getName();
                            for (OS os : OS.values()) {
                                for (String separator : List.of("-", ".")) {
                                    name = name
                                            .replace(separator + OS.current().name(), "")
                                            .replace(separator + OS.current().name().toLowerCase(Locale.ROOT), "")
                                            .replace(separator + arch.name(), "")
                                            .replace(separator + arch.name().toLowerCase(Locale.ROOT), "")
                                            .replace(separator + arch.dockerName(), "")
                                            .replace(separator + arch.dockerName().toUpperCase(Locale.ROOT), "")
                                            .replace(separator + "macos", "")
                                            .replace(separator + "osx", "");
                                }
                            }
                            return BaseCliPlugin.getExecutable(
                                    getProject(),
                                    normalizeName(name, allVersions)
                            );
                        },
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
                .forEach((linkName, target) -> {
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
