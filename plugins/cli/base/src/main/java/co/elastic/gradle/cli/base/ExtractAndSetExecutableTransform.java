package co.elastic.gradle.cli.base;

import org.apache.commons.compress.archivers.tar.TarArchiveEntry;
import org.apache.commons.compress.archivers.tar.TarArchiveInputStream;
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
        File inputFile = getInputArtifact().get().getAsFile();
        String executableName = inputFile.getName()
                .split("\\.transform")[0]
                .replace(".tar.xz", "");
        File outputFile = outputs.file(executableName);

        if (inputFile.getName().contains("tar.xz")) {
            try {
                try (TarArchiveInputStream archive = new TarArchiveInputStream(
                        new XZCompressorInputStream(
                                new BufferedInputStream(new FileInputStream(inputFile))
                        ))
                ) {
                    TarArchiveEntry entry;
                    while ((entry = archive.getNextTarEntry()) != null) {
                        try (OutputStream out = new BufferedOutputStream(new FileOutputStream(outputFile))) {
                            if (entry.isFile() && !List.of("README.txt", "LICENSE.txt").contains(entry.getName())) {
                                out.write(IOUtils.toByteArray(archive, entry.getSize()));
                            } else {
                                IOUtils.skip(archive, entry.getSize());
                            }
                        }
                    }
                }
            } catch (IOException e) {
                throw new UncheckedIOException(e);
            }
        } else {
            try {
                Files.copy(inputFile.toPath(), outputFile.toPath());
            } catch (IOException e) {
                throw new UncheckedIOException(e);
            }
        }

        if (!outputFile.setExecutable(true)) {
            throw new GradleException("Failed to set execute bit on " + outputFile);
        }
    }
}
