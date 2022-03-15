package co.elastic.gradle;

import org.junit.jupiter.api.Assertions;

import java.nio.file.Files;
import java.nio.file.Path;

abstract public class AssertFiles {

    public static void assertPathExists(Path path) {
        if (!Files.exists(path)) {
            Assertions.fail("Expected `" + path + "` to exist but it did not");
        }
    }

    public static void assertPathDoesNotExists(Path path) {
        if (Files.exists(path)) {
            Assertions.fail("Expected `" + path + "` not to exists");
        }
    }

}
