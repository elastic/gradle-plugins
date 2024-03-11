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
package co.elastic.gradle.lifecycle;

import co.elastic.gradle.utils.RegularFileUtils;
import org.gradle.api.DefaultTask;
import org.gradle.api.artifacts.Configuration;
import org.gradle.api.file.ProjectLayout;
import org.gradle.api.file.RegularFileProperty;
import org.gradle.api.tasks.*;

import javax.inject.Inject;
import java.io.IOException;
import java.nio.file.Files;
import java.util.Set;
import java.util.stream.Collectors;

public abstract class ResolveAllDependenciesTask extends DefaultTask {

    public ResolveAllDependenciesTask() {
        setDescription("Lifecycle task to resolves all external dependencies. " +
                            "This task can be used to cache everything locally so these are not  downloaded while building." +
                            " e.g. to download everything while on a better connection. In CI this is used to \"bake\" depdencies " +
                            "so they are not re-downloaded on every run."
        );
        setGroup("prepare");
        getMarkerFile().convention(
                getProjectLayout().getBuildDirectory().file(getName() + ".marker")
        );
    }

    @Inject
    protected abstract ProjectLayout getProjectLayout();

    @OutputFile
    public abstract RegularFileProperty getMarkerFile();

    @InputFiles
    @PathSensitive(PathSensitivity.NONE)
    public Set<Configuration> getResolvableConfigurations() {
        return getProject().getConfigurations().stream()
                .filter(Configuration::isCanBeResolved)
                // Resolving these will trigger a deprecation warning
                .filter(each -> ! Set.of("default", "archives").contains(each.getName()))
                .collect(Collectors.toSet());
    }

    @TaskAction
    public void resolveConfigurations() throws IOException {
        final Set<Configuration> resolvableConfigurations = getResolvableConfigurations();
        resolvableConfigurations.forEach(Configuration::resolve);
        Files.writeString(
                RegularFileUtils.toPath(getMarkerFile()),
                "Resolved configurations:" + resolvableConfigurations.size()
        );
    }

}
