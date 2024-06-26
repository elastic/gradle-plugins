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
package co.elastic.gradle.cli.shellcheck;

import co.elastic.gradle.utils.PrefixingOutputStream;
import co.elastic.gradle.utils.RegularFileUtils;
import org.gradle.api.DefaultTask;
import org.gradle.api.file.FileCollection;
import org.gradle.api.file.ProjectLayout;
import org.gradle.api.file.RegularFileProperty;
import org.gradle.api.tasks.*;
import org.gradle.process.ExecOperations;

import javax.inject.Inject;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;

@CacheableTask
public abstract class ShellcheckTask extends DefaultTask {

    private FileCollection filesToCheck = getProject().files();

    public ShellcheckTask() {
        getMarkerFile().convention(
                getProjectLayout().getBuildDirectory().file("shellcheck/" + getName() + ".marker")
        );
    }

    public void check(FileCollection files) {
        filesToCheck = filesToCheck.plus(files);
    }

    @InputFiles
    @PathSensitive(PathSensitivity.NAME_ONLY)
    @SkipWhenEmpty
    @IgnoreEmptyDirectories
    public FileCollection getFilesToCheck() {
        return filesToCheck;
    }

    @OutputFile
    public abstract RegularFileProperty getMarkerFile();

    @InputFile
    @PathSensitive(PathSensitivity.NAME_ONLY)
    public abstract RegularFileProperty getTool();

    @TaskAction
    void doCheck() throws IOException {
        getExecOperations().exec(spec -> {
            spec.setEnvironment(Collections.emptyMap());
            spec.setExecutable(getTool().get().getAsFile());
            spec.setStandardOutput(new PrefixingOutputStream("[shellcheck] ", System.out));
            spec.setErrorOutput(new PrefixingOutputStream("[shellcheck] ", System.err));
            spec.workingDir(getProject().getProjectDir());
            spec.setIgnoreExitValue(false);
            Path projectDir = getProject().getProjectDir().toPath();
            List<String> args = new ArrayList<>();
            // Don't read rc so we don't depend on local configuration
            args.add("--norc");
            args.add("--color=always");
            args.addAll(
                    getFilesToCheck().getFiles().stream()
                            .sorted()
                            .map(each -> projectDir.relativize(each.toPath()).toString())
                            .collect(Collectors.toCollection(() -> args))
            );
            spec.setArgs(args);
        });
        Files.writeString(RegularFileUtils.toPath(getMarkerFile()), "ran successfully");
    }

    @Inject
    protected abstract ExecOperations getExecOperations();

    @Inject
    protected abstract ProjectLayout getProjectLayout();

}
