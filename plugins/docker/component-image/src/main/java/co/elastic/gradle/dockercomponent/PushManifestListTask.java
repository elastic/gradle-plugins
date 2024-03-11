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

import co.elastic.gradle.cli.manifest.ManifestToolExecTask;
import co.elastic.gradle.utils.Architecture;
import co.elastic.gradle.utils.RegularFileUtils;
import co.elastic.gradle.utils.RetryUtils;
import org.gradle.api.GradleException;
import org.gradle.api.file.ProjectLayout;
import org.gradle.api.file.RegularFileProperty;
import org.gradle.api.provider.MapProperty;
import org.gradle.api.provider.Property;
import org.gradle.api.provider.Provider;
import org.gradle.api.tasks.Input;
import org.gradle.api.tasks.Internal;
import org.gradle.api.tasks.OutputFile;
import org.gradle.api.tasks.TaskAction;
import org.gradle.process.ExecOperations;
import org.gradle.process.ExecResult;
import org.jetbrains.annotations.NotNull;

import javax.inject.Inject;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.*;
import java.util.stream.Collectors;


abstract public class PushManifestListTask extends ManifestToolExecTask {


    public PushManifestListTask() {
        getDigestFile().convention(
                getProjectLayout().getBuildDirectory().file(getName() + ".digest")
        );
        setArgs(List.of("--version"));
    }

    @Inject
    abstract protected ProjectLayout getProjectLayout();

    @Input
    public abstract MapProperty<Architecture, String> getArchitectureTags();

    @Input
    public abstract Property<String> getTag();

    @OutputFile
    public abstract RegularFileProperty getDigestFile();

    @Internal
    public Provider<String> getDigest() {
        return getDigestFile().map(regularFile -> RegularFileUtils.readString(regularFile).trim());
    }

    @Inject
    protected abstract ExecOperations getExecOperations();

    @TaskAction
    public void pushManifestList() throws IOException {
        final Set<String> templates = getArchitectureTags().get().values().stream()
                .map(each -> {
                    String template = each;
                    for (Architecture value : Architecture.values()) {
                        template = template.replace(value.dockerName(), "ARCH");
                    }
                    return template;
                })
                .collect(Collectors.toSet());
        if (templates.isEmpty()) {
            throw new GradleException("Can't push manifest list, no input tags are present");
        }
        if (templates.size() > 1) {
            throw new GradleException("Can't derive template from manifest list: " + templates);
        }

        final Random random = new Random();
        final String output = RetryUtils.retry(() -> pushManifestList(templates))
                .maxAttempt(6)
                .exponentialBackoff(random.nextInt(5) * 1000, 30000)
                .onRetryError(error -> getLogger().warn("Error while pushing manifest. Retrying", error))
                .execute();

        if (output.startsWith("Digest: sha256:")) {
            Files.writeString(
                    RegularFileUtils.toPath(getDigestFile()),
                    output.substring(8, 79)
            );
            getLogger().lifecycle("Pushed manifest list to {}", getTag().get());
        } else {
            if (output.isEmpty()) {
                throw new GradleException("manifest-tool succeeded but generated no output. " +
                                          "Check the task output for additional details.");
            } else {
                throw new GradleException("manifest-tool succeeded but generated unexpected output: `" + output +
                        "`. Check the task output for additional details.");
            }
        }
    }

    @NotNull
    private String pushManifestList(Set<String> templates) {
        try {
            final ExecResult result;
            final String output;
            try (ByteArrayOutputStream out = new ByteArrayOutputStream()) {
                result = getExecOperations().exec(spec -> {
                    spec.setEnvironment(Collections.singletonMap(
                            "HOME", System.getProperty("user.home")
                    ));
                    spec.setExecutable(getExecutable());
                    spec.setArgs(Arrays.asList(
                            "push", "from-args",
                            "--platforms",
                            getArchitectureTags().get().keySet().stream()
                                    .map(each -> "linux/" + each.dockerName())
                                    .collect(Collectors.joining(",")),
                            "--template", templates.iterator().next(),
                            "--target", getTag().get()
                    ));
                    spec.setStandardOutput(out);
                    spec.setIgnoreExitValue(true);
                });
                output = out.toString(StandardCharsets.UTF_8).trim();
            }
            if (result.getExitValue() != 0) {
                throw new GradleException("Creating the manifest list failed: " + output);
            }
            return output;
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

}
