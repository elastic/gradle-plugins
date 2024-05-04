/*
 * Copyright Elasticsearch B.V. and/or licensed to Elasticsearch B.V. under one
 * or more contributor license agreements. See the NOTICE file distributed with
 * this work for additional information regarding copyright
 * ownership. Elasticsearch B.V. licenses this file to you under
 * the Apache License, Version 2.0 (the "License"); you may
 * not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *	http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied.  See the License for the
 * specific language governing permissions and limitations
 * under the License.
 */
package co.elastic.gradle.docker.base;

import org.apache.commons.compress.archivers.tar.TarArchiveEntry;
import org.apache.commons.compress.archivers.tar.TarArchiveInputStream;
import org.apache.commons.compress.compressors.zstandard.ZstdCompressorInputStream;
import org.apache.commons.io.IOUtils;

import java.io.*;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;

public class ExtractCompressedTar {

    public static InputStream uncompressedInputStream(final Path archive) throws IOException {
        InputStream result;
        InputStream imageStream = new BufferedInputStream(
                Files.newInputStream(archive, StandardOpenOption.READ)
        );
        byte[] magicBytes = new byte[4];
        imageStream.mark(4);
        if (imageStream.read(magicBytes) != 4) {
            throw new IOException("Failed to read magic bytes");
        }
        imageStream.reset();
        int magicNumber = ByteBuffer.wrap(magicBytes).order(ByteOrder.LITTLE_ENDIAN).getInt();
        // https://tools.ietf.org/html/rfc8478
        if (magicNumber == 0xFD2FB528) {
            result = new ZstdCompressorInputStream(imageStream);
        } else {
            result = imageStream;
        }
        return result;
    }

    public static void extract(final Path archive, final Path destination) throws IOException {
        Files.createDirectories(destination.getParent());
        TarArchiveInputStream tarStream = new TarArchiveInputStream(uncompressedInputStream(archive));
        TarArchiveEntry entry;
        while ((entry = tarStream.getNextTarEntry()) != null) {
            Path entryPath = destination.resolve(entry.getName());

            if (entry.isDirectory()) {
                Files.createDirectories(entryPath);
            } else {
                if (entryPath.getParent() != null) {
                    Files.createDirectories(entryPath.getParent());
                }
                try (OutputStream out = new BufferedOutputStream(Files.newOutputStream(entryPath))) {
                    IOUtils.copy(tarStream, out);
                }
            }
        }
    }

}
