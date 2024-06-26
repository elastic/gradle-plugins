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

import co.elastic.gradle.lifecycle.LifecyclePlugin;
import org.gradle.api.Plugin;
import org.gradle.api.Project;
import org.gradle.api.provider.ProviderFactory;
import org.gradle.api.tasks.TaskProvider;

import javax.inject.Inject;
import java.util.stream.Collectors;

public abstract class SandboxPlugin implements Plugin<Project> {

    @Inject
    public abstract ProviderFactory getProviderFactory();

    @Override
    public void apply(Project target) {
        final TaskProvider<DockerImagePull> resolveSandboxDockerDependencies = target.getTasks().register(
                "resolveSandboxDockerDependencies", DockerImagePull.class,
                t -> t.getTags().set(
                        getProviderFactory().provider(() ->
                                target.getTasks().withType(SandboxDockerExecTask.class)
                                        .stream()
                                        .filter(each -> each.getNeedsPull().get())
                                        .map(each -> each.getImage().get())
                                        .collect(Collectors.toList())
                        )
                )
        );
        target.getTasks().withType(SandboxDockerExecTask.class).configureEach(task ->
                task.dependsOn(resolveSandboxDockerDependencies)
        );

        LifecyclePlugin.resolveAllDependencies(target, resolveSandboxDockerDependencies);
    }
}
