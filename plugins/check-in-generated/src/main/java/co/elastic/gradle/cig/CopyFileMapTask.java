package co.elastic.gradle.cig;

import org.apache.commons.io.FileUtils;
import org.gradle.api.GradleException;
import org.gradle.api.tasks.*;

import java.io.File;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.util.Map;
import java.util.Set;

abstract public class CopyFileMapTask extends AbstractFileMapTask {

    @OutputFiles
    public Set<File> getToFiles() {
        return getFrom(getMap().get()::values, File::isFile);
    }

    @OutputDirectories
    public Set<File> getToDirs() {
        return getFrom(getMap().get()::values, File::isDirectory);
    }

    @TaskAction
    public void copyFiles() {
        final Map<File, File> map = getMap().get();
        if (map.isEmpty()) {
            throw new GradleException("The map of files can't be empty");
        }
        map.forEach((from, to) -> {
            if (!from.exists()) {
                throw new GradleException("Expected " + from + " to exist but it did not");
            }
            try {
                if (from.isDirectory()) {
                    FileUtils.copyDirectory(from, to);
                } else {
                    FileUtils.copyFile(from, to);
                }
            } catch (IOException e) {
                throw new UncheckedIOException(e);
            }
        });
    }

}
