package co.elatic.gradle.cig;

import org.gradle.api.DefaultTask;
import org.gradle.api.file.FileCollection;
import org.gradle.api.provider.MapProperty;
import org.gradle.api.tasks.InputFiles;
import org.gradle.api.tasks.Internal;
import org.gradle.api.tasks.PathSensitive;
import org.gradle.api.tasks.PathSensitivity;

import java.io.File;
import java.util.Collection;
import java.util.Set;
import java.util.function.Predicate;
import java.util.function.Supplier;
import java.util.stream.Collectors;

public abstract class AbstractFileMapTask extends DefaultTask {
    @Internal
    public abstract MapProperty<File, File> getMap();

    @InputFiles
    @PathSensitive(PathSensitivity.RELATIVE)
    public Set<File> getFromFiles() {
        return getFrom(getMap().get()::keySet, File::isFile);
    }

    @InputFiles // That's right, no InputDirectories in Gradle
    @PathSensitive(PathSensitivity.RELATIVE)
    public FileCollection getFromDirs() {
        return getFrom(getMap().get()::keySet, File::isDirectory).stream()
                .map( each -> (FileCollection) getProject().fileTree(each))
                .reduce(                        (tree1, tree2) -> {
                    final FileCollection files = getProject().files();
                    files.plus(tree1);
                    files.plus(tree2);
                    return files;
                })
                .orElse(getProject().files());
    }


    protected Set<File> getFrom(Supplier<Collection<File>> data, Predicate<File> filter) {
        return data.get()
                .stream()
                .filter(filter)
                .collect(Collectors.toSet());
    }
}
