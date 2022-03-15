package co.elastic.gradle;

import org.junit.jupiter.api.Assertions;

abstract public class AssertContains {

    public static void assertContains(String output, String expected) {
        if (! output.contains(expected)) {
            Assertions.fail("Expected `" + expected + "` to be part of, but it was not:\n" + output);
        }
    }

    public static void assertDoesNotContain(String output, String expected) {
        if (output.contains(expected)) {
            Assertions.fail("Expected `" + expected + "` NOT to be part of, but it was:\n" + output);
        }
    }

}
