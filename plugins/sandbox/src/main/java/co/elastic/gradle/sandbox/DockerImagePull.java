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
package co.elastic.gradle.sandbox;

import co.elastic.gradle.utils.RegularFileUtils;
import co.elastic.gradle.utils.docker.DockerUtils;
import org.gradle.api.DefaultTask;
import org.gradle.api.file.ProjectLayout;
import org.gradle.api.file.RegularFileProperty;
import org.gradle.api.provider.ListProperty;
import org.gradle.api.tasks.Input;
import org.gradle.api.tasks.OutputFile;
import org.gradle.api.tasks.TaskAction;
import org.gradle.process.ExecOperations;

import javax.inject.Inject;
import java.io.IOException;
import java.nio.file.Files;

public abstract class DockerImagePull extends DefaultTask {

    public DockerImagePull() {
        getMarkerFile().convention(
            getProjectLayout().getBuildDirectory().file("sandbox/" + getName() + ".marker")
        );
    }

    @Input
    abstract ListProperty<String> getTags();

    @Inject
    protected abstract ExecOperations getExecOperations();

    @Inject
    protected abstract ProjectLayout getProjectLayout();

    @OutputFile
    abstract RegularFileProperty getMarkerFile();

    @TaskAction
    public void pullImages() throws IOException {
        final DockerUtils dockerUtils = new DockerUtils(getExecOperations());
        getTags().get().forEach(tag -> {
            getLogger().lifecycle("Pulling docker image: {}", tag);
            dockerUtils.pull(tag);
        });
        Files.writeString(
                RegularFileUtils.toPath(getMarkerFile()),
                "Pulled the following tags:\n" + String.join("\n", getTags().get())
        );
    }

}
