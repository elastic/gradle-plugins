package co.elastic.gradle.utils;

import org.apache.commons.io.IOUtils;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;

public class SystemUtil {
    /**
     * Get the username for the current Unix user.
     *
     * @return the username for the current Unix user.
     */
    public String getUsername() {
        return System.getProperty("user.name");
    }

    /**
     * Get the UID for the current Unix user.
     *
     * @return the UID for the current Unix user.
     */
    public long getUid() {
        try {
            final Process process = Runtime.getRuntime().exec("id -u");
            return Long.parseLong(IOUtils.toString(process.getInputStream(), StandardCharsets.UTF_8).trim());
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    /**
     * Get the GID for the current Unix user.
     *
     * @return the GID for the current Unix user.
     */
    public long getGid() {
        try {
            final Process process = Runtime.getRuntime().exec("id -g");
            return Long.parseLong(IOUtils.toString(process.getInputStream(), StandardCharsets.UTF_8).trim());
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

}
