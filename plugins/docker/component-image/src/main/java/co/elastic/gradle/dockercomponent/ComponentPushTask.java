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
package co.elastic.gradle.dockercomponent;


import co.elastic.gradle.utils.Architecture;
import co.elastic.gradle.utils.GradleUtils;
import co.elastic.gradle.utils.RegularFileUtils;
import com.google.cloud.tools.jib.api.JibContainer;
import org.gradle.api.DefaultTask;
import org.gradle.api.Project;
import org.gradle.api.file.ProjectLayout;
import org.gradle.api.file.RegularFile;
import org.gradle.api.provider.MapProperty;
import org.gradle.api.provider.Provider;
import org.gradle.api.tasks.*;

import javax.inject.Inject;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.time.Instant;
import java.util.Collection;
import java.util.Map;
import java.util.Optional;
import java.util.function.Function;
import java.util.stream.Collectors;

abstract public class ComponentPushTask extends DefaultTask {

    public ComponentPushTask() {
        getDigestFiles().convention(
               getImageArchive().map(map -> map.entrySet().stream()
                           .collect(Collectors.toMap(
                                   Map.Entry::getKey,
                                   entry -> getProjectLayout().getBuildDirectory().file(entry.getValue().getAsFile().getName() + ".repoDigest")
                           )))
        );

        getTags().convention(
                getImageArchive().map(map -> map.keySet().stream()
                        .collect(Collectors.toMap(
                                Function.identity(),
                                architecture -> {
                                    Project project = getProject();
                                    String result;
                                    if (GradleUtils.isCi()) {
                                        // FIXME: This needs to be configurable!!
                                        result = "cloud-ci";
                                    } else {
                                        result = Optional.ofNullable(project.findProperty("co.elastic.docker.push.organization"))
                                                .map(String::valueOf)
                                                .orElse("gradle");
                                    }
                                    return "docker.elastic.co" + "/" +
                                           result + "/" +
                                           project.getName() + "-" + architecture.dockerName() +
                                           ":" + project.getVersion();
                                }
                        ))
                )
        );
    }

    @InputFiles
    public Collection<RegularFile> getAllImageArchives() {
        return getImageArchive().get().values();
    }

    @Input
    abstract public MapProperty<Architecture, String> getTags();

    @OutputFiles
    public Collection<Provider<RegularFile>> getAllDigestFiles() {
        return getDigestFiles().get().values();
    }

    @Internal
    public Provider<Map<Architecture, String>> getDigests() {
        return getDigestFiles().map(idFiles -> idFiles.entrySet()
                .stream()
                .collect(Collectors.toMap(Map.Entry::getKey, entry -> {
                    return RegularFileUtils.readString(entry.getValue().get()).trim();
                })));
    }

    @Internal
    abstract public MapProperty<Architecture, Provider<RegularFile>> getDigestFiles();

    @Internal
    abstract public MapProperty<Architecture, RegularFile> getImageArchive();

    @Internal
    abstract public MapProperty<Architecture, RegularFile> getCreatedAtFiles();

    @Inject
    abstract protected ProjectLayout getProjectLayout();

    @TaskAction
    public void pushImage() {
        final JibActions jibActions = new JibActions();
        getImageArchive().get().forEach((architecture, imageArchive) -> {
            final String tag = getTags().get().get(architecture);
            final RegularFile createdAtFile = getCreatedAtFiles().get().get(architecture);
            final Instant createdAt = Instant.parse(RegularFileUtils.readString(createdAtFile).trim());
            final JibContainer container = jibActions.pushImage(
                imageArchive.getAsFile().toPath(), 
                tag,
                createdAt
            );
            final String repoDigest = container.getDigest().toString();
            try {
                Files.writeString(
                        RegularFileUtils.toPath(getDigestFiles().get().get(architecture)),
                        repoDigest
                );
            } catch (IOException e) {
                throw new UncheckedIOException(e);
            }
            getLogger().lifecycle("Pushed image {}@{}", tag, repoDigest);
        });
    }


}



