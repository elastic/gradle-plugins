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
package co.elastic.gradle.wrapper;

import co.elastic.gradle.utils.Architecture;
import co.elastic.gradle.utils.OS;
import org.gradle.api.DefaultTask;
import org.gradle.api.GradleException;
import org.gradle.api.provider.MapProperty;
import org.gradle.api.provider.Property;
import org.gradle.api.tasks.Input;
import org.gradle.api.tasks.Optional;
import org.gradle.api.tasks.TaskAction;

import java.io.*;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import java.util.stream.Stream;

abstract public class ProvisionJDKInWrapperTask extends DefaultTask {

    public ProvisionJDKInWrapperTask() {
        getJdkCacheDir().convention("$HOME/.gradle/jdks");
    }

    @Input
    abstract public Property<String> getJavaReleaseName();

    @Input
    abstract public Property<String> getJdkCacheDir();

    @Input
    abstract public MapProperty<OS, Map<Architecture, String>> getChecksums();

    @Input
    @Optional
    abstract public Property<String> getAppleM1URLOverride();

    @TaskAction
    public void extendWrapper() throws IOException {
        final Map<OS, Map<Architecture, String>> checksums = getChecksums().get();
        final HashSet<OS> missingOS = new HashSet<>(List.of(OS.values()));
        missingOS.removeAll(checksums.keySet());
        if (!missingOS.isEmpty()) {
            throw new GradleException("A checksum for the following OSes missing:" + missingOS);
        }
        for (OS os : OS.values()) {
            final HashSet<Architecture> missingArch = new HashSet<>(List.of(Architecture.values()));
            missingArch.removeAll(
                    checksums.get(os).keySet()
            );
            if (!missingArch.isEmpty()) {
                throw new GradleException("Missing checksum for architecture on " + os + " : " + missingArch);
            }
        }
        final Path gradlewPath = getProject().getRootDir().toPath().resolve("gradlew");
        final Path gradlewNewPath = getProject().getRootDir().toPath().resolve("gradlew.new");
        try (
                final Stream<String> gradlew = Files.lines(gradlewPath)
        ) {
            Files.write(
                    gradlewNewPath,
                    gradlew.map(line -> {
                        if (line.startsWith("CLASSPATH=")) {
                            var javaMajor = getJavaReleaseName().get().split("\\.")[0];
                            final String downloadUrl = "" +
                                                       String.format(
                                                               "https://github.com/adoptium/temurin%s-binaries/releases/download/jdk-%s/OpenJDK%sU-jdk_${JDK_ARCH}_${JDK_OS}_hotspot_${JDK_VERSION}.tar.gz",
                                                               javaMajor,
                                                               URLEncoder.encode(getJavaReleaseName().get().replace("_", "+"), StandardCharsets.UTF_8),
                                                               javaMajor
                                                       );
                            String replaced = getCodeToDownloadJDK()
                                    .replace("%{JDK_VERSION}%", getJavaReleaseName().get())
                                    .replace("%{JDK_CACHE_DIR}%", getJdkCacheDir().get())
                                    .replace("%{JDK_DOWNLOAD_URL}%", downloadUrl)
                                    .replace(
                                            "%{JDK_DOWNLOAD_URL_M1_MAC}%",
                                            getAppleM1URLOverride().isPresent() ? getAppleM1URLOverride().get() : downloadUrl
                                    );


                            for (OS os : OS.values()) {
                                for (Architecture arch : Architecture.values()) {
                                    replaced = replaced.replace(
                                            "%{CHECKSUM_" + os + "_" + arch + "}%",
                                            checksums.get(os).get(arch)
                                    );
                                }
                            }
                            return line + "\n\n" + replaced
                                    ;
                        } else {
                            return line;
                        }
                    }).collect(Collectors.toList())
            );
        }
        if (!gradlewNewPath.toFile().setExecutable(true)) {
            throw new GradleException("Can't set execute bit on the wrapper");
        }
        Files.move(gradlewNewPath, gradlewPath, StandardCopyOption.REPLACE_EXISTING);
    }

    private String getCodeToDownloadJDK() {
        final InputStream is = getClass().getResourceAsStream("/gradlew-bootstrap-jdk.sh");
        if (is == null) {
            throw new IllegalStateException("Can't find /gradlew-bootstrap-jdk.sh in resources");
        }
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(is))) {
            return String.join("\n", reader.lines().toList());
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

}
