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

import co.elastic.gradle.dockercomponent.lockfile.ComponentLockfile;
import co.elastic.gradle.utils.RegularFileUtils;
import org.gradle.api.DefaultTask;
import org.gradle.api.file.RegularFileProperty;
import org.gradle.api.tasks.InputFile;
import org.gradle.api.tasks.StopExecutionException;
import org.gradle.api.tasks.TaskAction;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

public abstract class ComponentPullTask extends DefaultTask {

    @InputFile
    public abstract RegularFileProperty getLockfileLocation();

    @TaskAction
    public void pullImages() throws IOException {
        final Path lockfileLocation = RegularFileUtils.toPath(getLockfileLocation());
        if (!Files.exists(lockfileLocation)) {
            throw new StopExecutionException("Lockfile does not exist");
        }
        final ComponentLockfile lockFile = ComponentLockfile.parse(Files.newBufferedReader(lockfileLocation));
        final JibActions actions = new JibActions();
        lockFile.images().values().forEach(ref -> {
            final String format = String.format("%s:%s@%s", ref.getRepository(), ref.getTag(), ref.getDigest());
            getLogger().lifecycle("Pulling base layers for {} into the jib cache", format);
            actions.pullImage(format);
        });
    }

}
