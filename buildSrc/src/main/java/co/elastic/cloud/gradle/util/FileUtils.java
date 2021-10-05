package co.elastic.cloud.gradle.util;

import java.nio.file.Files;
import java.util.Map;
import org.gradle.api.file.RegularFile;
import org.gradle.api.GradleException;

public class FileUtils {

    public static String readFromRegularFile(RegularFile file) {
        try {
            return Files.readAllLines(file.getAsFile().toPath()).get(0);
        } catch (Exception e) {
            throw new GradleException("Internal error: Can't read from file: " + file, e);
        }
    }

}
