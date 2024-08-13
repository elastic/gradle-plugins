package co.elastic.gradle.utils;

import org.gradle.api.logging.Logger;
import org.gradle.api.logging.Logging;

import java.io.File;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.PosixFilePermissions;
import java.util.Map;
import java.util.stream.Collectors;

import static java.util.concurrent.TimeUnit.MILLISECONDS;
import static java.util.concurrent.TimeUnit.SECONDS;

/*
 * ELASTICSEARCH CONFIDENTIAL
 * __________________
 *
 *  Copyright Elasticsearch B.V. All rights reserved.
 *
 * NOTICE:  All information contained herein is, and remains
 * the property of Elasticsearch B.V. and its suppliers, if any.
 * The intellectual and technical concepts contained herein
 * are proprietary to Elasticsearch B.V. and its suppliers and
 * may be covered by U.S. and Foreign Patents, patents in
 * process, and are protected by trade secret or copyright
 * law.  Dissemination of this information or reproduction of
 * this material is strictly forbidden unless prior written
 * permission is obtained from Elasticsearch B.V.
 */
public interface CacheUtils {

    static final Logger logger = Logging.getLogger(CacheUtils.class);
    static final long EXPIRATION_BUFFER = MILLISECONDS.convert(2, SECONDS);
    static final boolean IS_CI =
            System.getenv("BUILD_URL") != null || System.getenv("BUILDKITE_BUILD_URL") != null;

    static Map<String, String> tryReadCache(Path leaseExpiration, Path data) {
        if (Files.exists(leaseExpiration)) {
            try {
                final long expireMillis = Long.parseLong(Files.readString(leaseExpiration));
                if (isValidLease(expireMillis)) {
                    return Files.list(data).collect(Collectors.toMap(
                            path -> path.getFileName().toString(),
                            path -> {
                                try {
                                    return Files.readString(path);
                                } catch (IOException e) {
                                    throw new UncheckedIOException(e);
                                }
                            }
                    ));
                }
            } catch (IOException e) {
                throw new UncheckedIOException(e);
            }
        }
        return null;
    }

    static void writeCacheDir(Path path, String content) {
        try {
            Files.createDirectories(path.getParent());
            Files.writeString(path, content);
            if (path.getFileSystem().supportedFileAttributeViews().contains("posix")) {
                Files.setPosixFilePermissions(
                        path,
                        PosixFilePermissions.fromString("rw-------")
                );
            } else {
                logger.warn("Not able to set {} to read only because the filesystem is not posix.", path);
            }
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    static boolean isValidLease(Long expirationMillis) {
        return expirationMillis - EXPIRATION_BUFFER > System.currentTimeMillis();
    }
}
