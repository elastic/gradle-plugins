package co.elastic.gradle.utils;

import org.gradle.api.GradleException;
import org.gradle.api.file.RegularFile;

import java.nio.file.Files;

public class RegularFileUtils {

    public static String readString(RegularFile file) {
        try {
            return Files.readString(file.getAsFile().toPath());
        } catch (Exception e) {
            throw new GradleException("Internal error: Can't read from file: " + file, e);
        }
    }


}
