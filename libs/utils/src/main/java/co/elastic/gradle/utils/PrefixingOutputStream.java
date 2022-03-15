package co.elastic.gradle.utils;

import java.io.IOException;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;

public class PrefixingOutputStream extends OutputStream {

    private final byte[] prefix;
    private boolean firstByteWritten = false;
    private final OutputStream delegate;

    public PrefixingOutputStream(String prefix, OutputStream delegate) {
        this.prefix = prefix.getBytes(StandardCharsets.UTF_8);
        this.delegate = delegate;
    }

    @Override
    public void write(int b) throws IOException {
        if (!firstByteWritten) {
            delegate.write(prefix);
            firstByteWritten = true;
        }
        delegate.write(b);
        if (b == '\n') {
            delegate.write(prefix);
        }
    }

}
