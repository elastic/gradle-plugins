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

import co.elastic.gradle.dockerbase.DockerBaseImageBuildPlugin;
import co.elastic.gradle.dockerbase.DockerBaseImageBuildTask;
import co.elastic.gradle.utils.Architecture;
import co.elastic.gradle.utils.docker.instruction.*;
import kotlin.Pair;
import org.gradle.api.Action;
import org.gradle.api.Project;
import org.gradle.api.file.CopySpec;
import org.gradle.api.provider.ProviderFactory;
import org.gradle.api.tasks.TaskProvider;

import java.util.ArrayList;
import java.util.List;

public class ComponentBuildDSL {
    private final List<ContainerImageBuildInstruction> instructions;

    private final Architecture architecture;
    private final ProviderFactory providerFactory;

    public ComponentBuildDSL(Architecture architecture, ProviderFactory providerFactory) {
        this.architecture = architecture;
        instructions = new ArrayList<>();
        this.providerFactory = providerFactory;
    }

    public List<ContainerImageBuildInstruction> getInstructions() {
        return instructions;
    }

    @SuppressWarnings("unused")
    public Architecture getArchitecture() {
        return architecture;
    }

    public void from(String image, String version) {
        instructions.add(new From(
                providerFactory.provider(() -> String.format("%s:%s", image, version))
        ));
    }

    public void from(Project otherProject) {
        final TaskProvider<DockerBaseImageBuildTask> dockerBaseImageBuild = otherProject.getTasks()
                .named("dockerBaseImageBuild", DockerBaseImageBuildTask.class);
        // For this use-case either a local image or a remote reference can be used, so we add both instructions and
        // expect tasks to filter as they need
        instructions.add(
                new FromLocalArchive(
                        dockerBaseImageBuild.flatMap(task -> task.getImageArchive().getAsFile()),
                        dockerBaseImageBuild.flatMap(DockerBaseImageBuildTask::getImageId)
                )
        );
        instructions.add(new From(DockerBaseImageBuildPlugin.pushedTagConvention(otherProject, architecture)));
    }

    @SuppressWarnings("unused")
    public void maintainer(String name, String email) {
        instructions.add(
                new Maintainer(name, email)
        );
    }

    public void copySpec(String owner, Action<CopySpec> copySpecAction) {
        instructions.add(
                new Copy(
                        copySpecAction,
                        architecture.name().toLowerCase() + "-layer" + instructions.size(),
                        owner
                )
        );
    }

    @SuppressWarnings("unused")
    public void copySpec(Action<CopySpec> copySpecAction) {
        copySpec(null, copySpecAction);
    }

    @SuppressWarnings("unused")
    public void entryPoint(List<String> entrypoint) {
        instructions.add(new Entrypoint(entrypoint));
    }

    @SuppressWarnings("unused")
    public void cmd(List<String> cmd) {
        instructions.add(new Cmd(cmd));
    }

    @SuppressWarnings("unused")
    public void env(Pair<String, String> value) {
        instructions.add(new Env(value.component1(), value.component2()));
    }

    @SuppressWarnings("unused")
    public void workDir(String dir) {
        instructions.add(new Workdir(dir));
    }

    @SuppressWarnings("unused")
    public void exposeTcp(Integer port) {
        instructions.add(new Expose(Expose.Type.TCP, port));
    }

    @SuppressWarnings("unused")
    public void exposeUdp(Integer port) {
        instructions.add(new Expose(Expose.Type.UDP, port));
    }

    @SuppressWarnings("unused")
    public void label(Pair<String, String> value) {
        instructions.add(new Label(value.component1(), value.component2()));
    }

    @SuppressWarnings("unused")
    public void changingLabel(Pair<String, String> value) {
        instructions.add(new ChangingLabel(value.component1(), value.component2()));
    }

}