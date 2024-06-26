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
package co.elastic.gradle.docker.base;

import co.elastic.gradle.utils.docker.DockerUtils;
import org.gradle.api.DefaultTask;
import org.gradle.api.provider.Property;
import org.gradle.api.tasks.Input;
import org.gradle.api.tasks.TaskAction;
import org.gradle.process.ExecOperations;

import javax.inject.Inject;

abstract public class DockerLocalCleanTask extends DefaultTask {

    @TaskAction
    public void cleanUpImages() {
        DockerUtils dockerUtils = new DockerUtils(getExecOperations());
        dockerUtils.exec(spec -> {
            spec.commandLine("docker", "image", "rm", getImageTag().get());
            spec.setIgnoreExitValue(true);
        });
    }

    @Input
    abstract public Property<String> getImageTag();

    @Inject
    abstract public ExecOperations getExecOperations();


}
