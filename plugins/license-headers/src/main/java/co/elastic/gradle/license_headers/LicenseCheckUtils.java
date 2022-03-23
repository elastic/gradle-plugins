package co.elastic.gradle.license_headers;

import org.gradle.api.GradleException;

import java.io.File;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class LicenseCheckUtils {

    public static Map<Path, ViolationReason> nonCompliantFilesWithReason(Path projectDir, String[] expectedHeader, List<File> files) {
        Map<Path, ViolationReason> brokenFiles = new HashMap<>();

        for (File file : files) {
            final String[] fileHeader;
            try {
                fileHeader = Files.lines(file.toPath()).limit(expectedHeader.length).toArray(String[]::new);
            } catch (IOException| UncheckedIOException e) {
                throw new GradleException("Failed to read " + projectDir.relativize(file.toPath()), e);
            }
            if (expectedHeader.length > fileHeader.length) {
                brokenFiles.put(
                        file.toPath(),
                        new ViolationReason("File has fewer lines than the header", ViolationReason.Type.SHORT_FILE)
                );
            } else for (int i = 0; i < expectedHeader.length; i++) {
                if (!fileHeader[i].equals(expectedHeader[i])) {
                    if (i == 0) {
                        brokenFiles.put(
                                file.toPath(),
                                new ViolationReason("Missing header", ViolationReason.Type.MISSING_HEADER)
                        );
                    } else {
                        brokenFiles.put(
                                file.toPath(),
                                new ViolationReason("Header mismatch at line " + (i + 1), ViolationReason.Type.LINE_MISS_MATCH)
                        );
                    }
                    break;
                }
            }
        }

        return brokenFiles;
    }

}
