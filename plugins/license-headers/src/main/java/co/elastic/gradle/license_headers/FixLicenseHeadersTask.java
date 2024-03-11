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
package co.elastic.gradle.license_headers;

import co.elastic.gradle.utils.RegularFileUtils;
import org.gradle.api.DefaultTask;
import org.gradle.api.file.ProjectLayout;
import org.gradle.api.provider.ListProperty;
import org.gradle.api.tasks.Nested;
import org.gradle.api.tasks.TaskAction;

import javax.inject.Inject;
import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;

public abstract class FixLicenseHeadersTask extends DefaultTask {

    @Nested
    public abstract ListProperty<LicenseHeaderConfig> getConfigs();

    @TaskAction
    public void fixHeaders() throws IOException {
        for (LicenseHeaderConfig c : getConfigs().get()) {
            Path projectDir = getProjectLayout().getProjectDirectory().getAsFile().toPath();
            final String[] expectedHeader = Files.readAllLines(RegularFileUtils.toPath(c.getHeaderFile())).toArray(String[]::new);
            final List<File> files = c.getFiles().get();
            final Map<Path, ViolationReason> brokenPaths = LicenseCheckUtils.nonCompliantFilesWithReason(
                    projectDir, expectedHeader, files
            );
            for (Map.Entry<Path, ViolationReason> entry : brokenPaths.entrySet()) {
                if (entry.getValue().type().equals(ViolationReason.Type.LINE_MISS_MATCH)) {
                    getLogger().warn("Can't automatically fix `{}` : {}", projectDir.relativize(entry.getKey()), entry.getValue().reason());
                } else {
                    Files.write(
                            entry.getKey(),
                            (Iterable<String>) Stream.concat(
                                    Arrays.stream(expectedHeader),
                                    Files.lines(entry.getKey())
                            )::iterator
                    );
                    getLogger().lifecycle("Added header to {}", projectDir.relativize(entry.getKey()));
                }
            }
        }
    }


    @Inject
    protected abstract ProjectLayout getProjectLayout();
}

