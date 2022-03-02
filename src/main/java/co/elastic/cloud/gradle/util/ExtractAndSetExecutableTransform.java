package co.elastic.cloud.gradle.util;

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
import org.gradle.api.tasks.Input;

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.io.UncheckedIOException;
import java.nio.file.Files;

public abstract class ExtractAndSetExecutableTransform implements TransformAction<TransformParameters.None> {

    @InputArtifact
    public abstract Provider<FileSystemLocation> getInputArtifact();

    @Override
    public void transform(TransformOutputs outputs) {
        File inputFile = getInputArtifact().get().getAsFile();
        String executableName = inputFile.getName().split("-v")[0];
        File outputFile = outputs.file(executableName);

        if (inputFile.getName().contains("tar.xz")) {
            try {
                try (TarArchiveInputStream archive = new TarArchiveInputStream(
                        new XZCompressorInputStream(
                                new BufferedInputStream(new FileInputStream(inputFile))
                        ));
                     OutputStream out = new BufferedOutputStream(new FileOutputStream(outputFile))
                ) {
                    TarArchiveEntry entry;
                    while ((entry = archive.getNextTarEntry()) != null) {
                        if (entry.isFile() && entry.getName().endsWith(executableName)) {
                            out.write(IOUtils.toByteArray(archive, entry.getSize()));
                            break;
                        } else {
                            IOUtils.skip(archive, entry.getSize());
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
