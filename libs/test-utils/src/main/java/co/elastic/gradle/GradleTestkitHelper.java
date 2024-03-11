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
package co.elastic.gradle;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.PosixFilePermission;
import java.util.List;
import java.util.Set;

public record GradleTestkitHelper(Path projectDir) {

    public void settings(String content) {
        writeFile(projectDir.resolve("settings.gradle.kts"), content);
    }

    public void buildScript(String content) {
        writeFile(projectDir.resolve("build.gradle.kts"), content);
    }

    public void buildScript(String subprojectPath, String content) {
        writeFile(projectDir.resolve(subprojectPath).resolve("build.gradle.kts"), content);
    }

    public void writeScript(String path, String contents) {
        Path scriptPath = projectDir.resolve(path);
        writeFile(scriptPath, contents);
        final Set<PosixFilePermission> permissions;
        try {
            permissions = Files.getPosixFilePermissions(scriptPath);
            permissions.add(PosixFilePermission.OWNER_EXECUTE);
            Files.setPosixFilePermissions(scriptPath, permissions);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    public void writeFile(String path, String contents) {
        writeFile(projectDir.resolve(path), contents);
    }

    private void writeFile(Path path, String content) {
        final Path relativePath = projectDir.relativize(path);
        System.out.println(relativePath);
        System.out.println("-".repeat(relativePath.toString().length()));
        final List<String> lines = content.lines().toList();
        for (int i = 1; i <= lines.size(); i++) {
            System.out.println(String.format("%1$2s:", i) + lines.get(i - 1));
        }
        System.out.println();
        try {
            Files.createDirectories(path.getParent());
            Files.writeString(path, content, StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }
}
