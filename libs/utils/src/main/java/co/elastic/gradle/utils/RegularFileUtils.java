package co.elastic.gradle.utils;

import org.gradle.api.GradleException;
import org.gradle.api.file.FileSystemLocation;
import org.gradle.api.file.RegularFile;
import org.gradle.api.provider.Provider;

import java.nio.file.Files;
import java.nio.file.Path;

public class RegularFileUtils {

    public static String readString(RegularFile file) {
        try {
            return Files.readString(file.getAsFile().toPath());
        } catch (Exception e) {
            throw new GradleException("Internal error: Can't read from file: " + file, e);
        }
    }

    public static Path toPath(Provider<? extends FileSystemLocation> loc) {
        return loc.get().getAsFile().toPath();
    }


}
