package co.elastic.gradle.utils;

import org.junit.jupiter.api.Test;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;

import static org.junit.jupiter.api.Assertions.*;

class PrefixingOutputStreamTest {

    @Test
    void testPrefix() throws IOException {
        final ByteArrayOutputStream delegate = new ByteArrayOutputStream();
        new PrefixingOutputStream("[test] ", delegate).write("""
                This is a test
                To show that\r
                each line is prefixed.""".getBytes(StandardCharsets.UTF_8)
        );

        assertEquals("[test] This is a test\n" +
                     "[test] To show that\r\n" +
                     "[test] each line is prefixed.",
                delegate.toString(StandardCharsets.UTF_8)
        );
    }
}