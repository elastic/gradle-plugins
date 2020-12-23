/*
 * Copyright 2019 Google LLC.
 *
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not
 * use this file except in compliance with the License. You may obtain a copy of
 * the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS, WITHOUT
 * WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the
 * License for the specific language governing permissions and limitations under
 * the License.
 */

package com.google.cloud.tools.jib.tar;

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.file.Files;
import java.nio.file.Path;
import org.apache.commons.compress.archivers.tar.TarArchiveEntry;
import org.apache.commons.compress.archivers.tar.TarArchiveInputStream;
import org.apache.commons.compress.compressors.zstandard.ZstdCompressorInputStream;
import org.apache.commons.io.IOUtils;
import org.jetbrains.annotations.NotNull;

// HACK: Jib doesn't support compressed tar archives, see https://github.com/GoogleContainerTools/jib/issues/2895
//       Thus we replace the class that deals with extracting tar archives to add support for it.
//       Ideally we'll contribute it upstream and will be able to remove the hack.

/** Extracts a tarball. */
public class TarExtractor {

    /**
     * Extracts a tarball to the specified destination.
     *
     * @param source the tarball to extract
     * @param destination the output directory
     * @throws IOException if extraction fails
     */
    public static void extract(Path source, Path destination) throws IOException {
        String canonicalDestination = destination.toFile().getCanonicalPath();

        // Decompress image from zstd <- this is a change from upstream
        BufferedInputStream inFile = new BufferedInputStream(Files.newInputStream(source));
        try (TarArchiveInputStream tarArchiveInputStream = new TarArchiveInputStream(getSpecificInputStream(inFile))) {

            for (TarArchiveEntry entry = tarArchiveInputStream.getNextTarEntry();
                 entry != null;
                 entry = tarArchiveInputStream.getNextTarEntry()) {
                Path entryPath = destination.resolve(entry.getName());

                String canonicalTarget = entryPath.toFile().getCanonicalPath();
                if (!canonicalTarget.startsWith(canonicalDestination + File.separator)) {
                    String offender = entry.getName() + " from " + source;
                    throw new IOException("Blocked unzipping files outside destination: " + offender);
                }

                if (entry.isDirectory()) {
                    Files.createDirectories(entryPath);
                } else {
                    if (entryPath.getParent() != null) {
                        Files.createDirectories(entryPath.getParent());
                    }
                    try (OutputStream out = new BufferedOutputStream(Files.newOutputStream(entryPath))) {
                        // change from upstream so we don't add additional dependencies
                        IOUtils.copy(tarArchiveInputStream, out);
                    }
                }
            }
        }
    }

    @NotNull
    public static InputStream getSpecificInputStream(BufferedInputStream inFile) throws IOException {
        InputStream archiveStream;
        byte[] magicBytes = new byte[4];
        inFile.mark(4);
        inFile.read(magicBytes);
        inFile.reset();
        int magicNumber = ByteBuffer.wrap(magicBytes).order(ByteOrder.LITTLE_ENDIAN).getInt();
        // https://tools.ietf.org/html/rfc8478
        if (magicNumber == 0xFD2FB528) {
            archiveStream = new ZstdCompressorInputStream(inFile);
        } else {
            archiveStream = inFile;
        }
        return archiveStream;
    }
}
