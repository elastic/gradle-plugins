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
package co.elastic.gradle.dockerbase;

import co.elastic.gradle.utils.Architecture;
import co.elastic.gradle.utils.RegularFileUtils;
import com.google.cloud.tools.jib.api.JibContainer;
import org.gradle.api.DefaultTask;
import org.gradle.api.file.ProjectLayout;
import org.gradle.api.file.RegularFileProperty;
import org.gradle.api.provider.Property;
import org.gradle.api.tasks.*;

import javax.inject.Inject;
import java.io.IOException;
import java.nio.file.Files;
import java.time.Instant;

abstract public class DockerPushTask extends DefaultTask {

    @Inject
    public DockerPushTask() {
        final String baseFileName = getName() + "/" + "repo-" + Architecture.current().name().toLowerCase();
        getDigestFile().convention(
                getProjectLayout().getBuildDirectory().file(baseFileName + ".repoDigest")
        );
    }

    @OutputFile
    public abstract RegularFileProperty getDigestFile();

    @Inject
    abstract protected ProjectLayout getProjectLayout();

    @Input
    public abstract Property<String> getTag();

    @Input
    public abstract Property<Instant> getCreatedAt();

    @InputFile
    @PathSensitive(PathSensitivity.NONE)
    public abstract RegularFileProperty getImageArchive();

    @TaskAction
    public void pushImage() throws IOException {
        final String tag = getTag().get();
        final Instant createdAt = getCreatedAt().get();
        final JibContainer container = new JibPushActions().pushImage(
                RegularFileUtils.toPath(getImageArchive()),
                tag,
                createdAt
        );

        final String repoDigest = container.getDigest().toString();
        Files.writeString(
                RegularFileUtils.toPath(getDigestFile()),
                repoDigest
        );
        getLogger().lifecycle("Pushed image {}@{}", tag, repoDigest);
    }
}
