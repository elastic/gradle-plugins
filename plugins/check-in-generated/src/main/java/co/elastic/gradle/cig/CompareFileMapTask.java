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
package co.elastic.gradle.cig;

import org.apache.commons.io.FileUtils;
import org.gradle.api.GradleException;
import org.gradle.api.file.FileCollection;
import org.gradle.api.file.ProjectLayout;
import org.gradle.api.file.RegularFileProperty;
import org.gradle.api.tasks.*;

import javax.inject.Inject;
import java.io.File;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;

@CacheableTask
public abstract class CompareFileMapTask extends AbstractFileMapTask {

    public CompareFileMapTask() {
        getMarker().convention(getProjectLayout().getBuildDirectory().file(getName() + ".marker"));
    }

    @InputFiles
    @PathSensitive(PathSensitivity.RELATIVE)
    public Set<File> getToFiles() {
        return getFrom(getMap().get()::values, File::isFile);
    }

    @InputFiles // That's right, no InputDirectories in Gradle
    @PathSensitive(PathSensitivity.RELATIVE)
    public FileCollection getToDirs() {
        return getFrom(getMap().get()::values, File::isDirectory).stream()
                .map( each -> (FileCollection) getProject().fileTree(each))
                .reduce(                        (tree1, tree2) -> {
                    final FileCollection files = getProject().files();
                    files.plus(tree1);
                    files.plus(tree2);
                    return files;
                })
                .orElse(getProject().files());
    }

    @OutputFile
    public abstract RegularFileProperty getMarker();

    @Inject
    public abstract ProjectLayout getProjectLayout();

    @TaskAction
    public void doCheck() throws IOException {
        final Map<File, File> map = getMap().get();

        if (map.isEmpty()) {
            throw new GradleException("The map of files can't be empty");
        }

        try (final Stream<Map.Entry<File, File>> stream = map.entrySet().stream()) {
            final List<Path> nonEqualFiles = stream
                    .flatMap(entry -> nonEqualFiles(entry.getKey().toPath(), entry.getValue().toPath()))
                    .toList();

            if (!nonEqualFiles.isEmpty()) {
                Path projectDir = getProject().getProjectDir().toPath();
                throw new GradleException(
                        "This projects expects some generated files to be checked in, but it looks like this did not happen.\n" +
                        "Please make sure your working copy is up-to-date and run `./gradlew generate` and commit the changed files.\n " +
                        "The following files did not match what was generated:\n   - " +
                        String.join(
                                "\n   - ",
                                nonEqualFiles.stream()
                                        .map(projectDir::relativize)
                                        .map(Path::toString)
                                        .collect(Collectors.toSet())
                        )
                );
            }
        }

    }

    private Stream<Path> nonEqualFiles(Path file1, Path file2) {
        if (!Files.exists(file1) || !Files.exists(file2)) {
            return Stream.of(file2);
        }
        try {
            if (Files.isDirectory(file1)) {
                if (Files.isDirectory(file2)) {
                    return Files.walk(file1)
                            .filter(each -> !each.equals(file1))
                            .flatMap(each -> nonEqualFiles(
                                    each,
                                    file2.resolve(file1.relativize(each))
                            ));

                } else {
                    return Stream.of(file2);
                }
            } else {
                if (Files.isDirectory(file2)) {
                    return Stream.of(file2);
                } else {
                    if (FileUtils.contentEquals(file1.toFile(), file2.toFile())) {
                        return Stream.of();
                    } else {
                        return Stream.of(file2);
                    }
                }
            }
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }
}
