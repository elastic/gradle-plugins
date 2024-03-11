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
package co.elastic.gradle.cli.base;

import org.apache.commons.compress.archivers.tar.TarArchiveEntry;
import org.apache.commons.compress.archivers.tar.TarArchiveInputStream;
import org.apache.commons.compress.compressors.gzip.GzipCompressorInputStream;
import org.apache.commons.compress.compressors.xz.XZCompressorInputStream;
import org.apache.commons.io.IOUtils;
import org.gradle.api.GradleException;
import org.gradle.api.artifacts.transform.InputArtifact;
import org.gradle.api.artifacts.transform.TransformAction;
import org.gradle.api.artifacts.transform.TransformOutputs;
import org.gradle.api.artifacts.transform.TransformParameters;
import org.gradle.api.file.FileSystemLocation;
import org.gradle.api.provider.Provider;

import java.io.*;
import java.nio.file.Files;
import java.util.List;

public abstract class ExtractAndSetExecutableTransform implements TransformAction<TransformParameters.None> {

    @InputArtifact
    public abstract Provider<FileSystemLocation> getInputArtifact();

    @Override
    public void transform(TransformOutputs outputs) {
        try {
            doTransform(outputs);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    private void doTransform(TransformOutputs outputs) throws IOException {
        File inputFile = getInputArtifact().get().getAsFile();
        String executableName = inputFile.getName()
                .split("\\.transform")[0]
                .replace(".tar.xz", "");

        if (inputFile.getName().contains("tar.xz")) {
            try (TarArchiveInputStream archive = new TarArchiveInputStream(
                    new XZCompressorInputStream(
                            new BufferedInputStream(new FileInputStream(inputFile))
                    ))
            ) {
                TarArchiveEntry entry;
                while ((entry = archive.getNextTarEntry()) != null) {
                    if (entry.isFile() && !List.of("README.txt", "LICENSE.txt").contains(entry.getName())) {
                        final File outputFile = outputs.file(executableName);
                        try (OutputStream out = new BufferedOutputStream(new FileOutputStream(outputFile))) {
                            out.write(IOUtils.toByteArray(archive, entry.getSize()));
                            setExecutableBit(outputFile);
                        }
                    } else {
                        IOUtils.skip(archive, entry.getSize());
                    }
                }
            }
        } else if (inputFile.getName().contains("manifest-tool") && !inputFile.getName().contains("v1")) {
            try (TarArchiveInputStream archive = new TarArchiveInputStream(
                    new GzipCompressorInputStream(
                            new BufferedInputStream(new FileInputStream(inputFile))
                    ))
            ) {
                TarArchiveEntry entry;
                while ((entry = archive.getNextTarEntry()) != null) {
                    final File outputFile = outputs.file(entry.getName());
                    try (OutputStream out = new BufferedOutputStream(new FileOutputStream(outputFile))) {
                        out.write(IOUtils.toByteArray(archive, entry.getSize()));
                    }
                    setExecutableBit(outputFile);
                }
            }
        } else {
            File outputFile = outputs.file(executableName);
            Files.copy(inputFile.toPath(), outputFile.toPath());
            setExecutableBit(outputFile);
        }
    }

    private void setExecutableBit(File outputFile) {
        if (!outputFile.setExecutable(true)) {
            throw new GradleException("Failed to set execute bit on " + outputFile);
        }
    }
}
