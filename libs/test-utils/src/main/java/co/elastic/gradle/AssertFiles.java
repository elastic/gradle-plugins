package co.elastic.gradle;

import org.junit.jupiter.api.Assertions;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.stream.Collectors;
import java.util.stream.Stream;

abstract public class AssertFiles {

    public static void assertPathExists(Path path) {
        if (!Files.exists(path)) {
            String additional = "";
            if (Files.exists(path.getParent())) {
                try (Stream<Path>stream = Files.list(path.getParent())) {
                    additional = "\n parent directory contains:\n    " +
                                 stream
                                         .map(Path::toString)
                                         .collect(Collectors.joining("\n    "));
                } catch (IOException e) {
                    throw new UncheckedIOException(e);
                }
            } else {
                additional = "\nParent path doesn't exist either";
            }
            Assertions.fail("Expected `" + path + "` to exist but it did not." + additional);
        }
    }

    public static void assertPathDoesNotExists(Path path) {
        if (Files.exists(path)) {
            Assertions.fail("Expected `" + path + "` not to exists");
        }
    }

}
