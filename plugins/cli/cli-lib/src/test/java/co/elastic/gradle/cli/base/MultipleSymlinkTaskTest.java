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

import org.gradle.api.Project;
import org.gradle.testfixtures.ProjectBuilder;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class MultipleSymlinkTaskTest {
    @TempDir
    Path directory;

    @Test
    void mixesNativeAndEmulatedToolsAndReplacesDanglingLinks() throws IOException {
        Project project = ProjectBuilder.builder().withProjectDir(directory.toFile()).build();
        project.getConfigurations().create(BaseCliPlugin.CONFIGURATION_NAME);
        Path repository = Files.createDirectory(directory.resolve("repository"));
        project.getRepositories().ivy(repo -> {
            repo.setUrl(repository);
            repo.patternLayout(layout -> layout.artifact("[module]-[revision]-[classifier]"));
            repo.metadataSources(sources -> sources.artifact());
        });
        for (String coordinate : List.of(
                "fixture:manifest-tool:v1.0.3:darwin-amd64",
                "fixture:snyk:v1.1187.0:macos",
                "fixture:native:v1.0.0:darwin-amd64",
                "fixture:native:v1.0.0:darwin-arm64")) {
            String[] parts = coordinate.split(":");
            Files.writeString(repository.resolve(parts[1] + "-" + parts[2] + "-" + parts[3]), "fixture");
            project.getDependencies().add(BaseCliPlugin.CONFIGURATION_NAME, coordinate + "@bin");
        }
        MultipleSymlinkTask task = project.getTasks().create("link", MultipleSymlinkTask.class);
        // Resolve with the host JVM's platform before simulating Apple Silicon's selection.
        project.getConfigurations().getByName(BaseCliPlugin.CONFIGURATION_NAME).resolve();
        String originalOS = System.getProperty("os.name");
        String originalArch = System.getProperty("os.arch");
        try {
            System.setProperty("os.name", "Mac OS X");
            System.setProperty("os.arch", "aarch64");
            Map<File, File> links = task.getNameToTargetMap();
            Path bin = project.getRootDir().toPath().resolve(".gradle/bin");
            assertTrue(links.containsKey(bin.resolve("manifest-tool").toFile()), links.toString());
            assertEquals("manifest-tool-v1.0.3-darwin-amd64", links.get(bin.resolve("manifest-tool").toFile()).getName());
            assertEquals("snyk-v1.1187.0-macos", links.get(bin.resolve("snyk").toFile()).getName());
            assertEquals("native-v1.0.0-darwin-arm64", links.get(bin.resolve("native").toFile()).getName());
            assertTrue(task.getTarget().containsAll(links.values()));
            assertTrue(task.getLinkName().containsAll(links.keySet()));
            Files.createDirectories(bin);
            Files.createSymbolicLink(bin.resolve("manifest-tool"), directory.resolve("missing-executable"));
            task.doLink();
            assertEquals(links.get(bin.resolve("manifest-tool").toFile()).toPath(), Files.readSymbolicLink(bin.resolve("manifest-tool")));
            task.doLink();
            assertEquals(links.get(bin.resolve("manifest-tool").toFile()).toPath(), Files.readSymbolicLink(bin.resolve("manifest-tool")));
        } finally {
            System.setProperty("os.name", originalOS);
            System.setProperty("os.arch", originalArch);
        }
    }
}
