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

import co.elastic.gradle.utils.Architecture;
import co.elastic.gradle.utils.SystemUtil;
import co.elastic.gradle.utils.docker.ContainerImageProviderTask;
import co.elastic.gradle.utils.docker.DockerUtils;
import org.gradle.api.GradleException;
import org.gradle.api.Project;
import org.gradle.api.provider.Property;
import org.gradle.api.tasks.*;
import org.gradle.process.ExecResult;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.Stream;

@CacheableTask
abstract public class SandboxDockerExecTask extends SandboxExecBaseTask {

    public SandboxDockerExecTask() {
       super();
       getNeedsPull().convention(false);
    }

    @Input
    abstract Property<String> getImage();

    @Input
    @Optional
    abstract Property<String> getImageId();

    @Input
    abstract Property<Boolean> getNeedsPull();


    private String getUserString() {
        SystemUtil unixSystem = new SystemUtil();
        long uid = unixSystem.getUid();
        long gid = unixSystem.getGid();
        return String.format("%d:%d", uid, gid);
    }

    @Override
    protected ExecResult doExec() {
        return new DockerUtils(getExecOperations()).exec(spec -> {
            // bind mount the sandbox to /sandbox and set the working dir within
            // We need to pass --platform explicitly here to make sure hat Docker Desktop on the M1 does not start
            // emulating these.
            List<String> dockerCmdLine = new ArrayList<>(Arrays.asList(
                    "docker", "run", "--platform", "linux/" + Architecture.current().dockerName(),
                    "--rm", "-w", "/sandbox/" + sandbox.relativize(workingDirectory),
                    "-v", sandbox + ":/sandbox/", "--user", getUserString()
            ));
            // Pass the environment to the docker process and only add their names to the CLI
            environment.keySet().stream()
                    .flatMap(each -> Stream.of("-e", each))
                    .collect(Collectors.toCollection(() -> dockerCmdLine));
            spec.setEnvironment(environment);
            dockerCmdLine.add(getImage().get());
            dockerCmdLine.addAll(commandLine);
            getLogger().info("Running docker command: {}", dockerCmdLine);
            spec.setCommandLine(dockerCmdLine);
            spec.setIgnoreExitValue(true);
        });
    }


    @SuppressWarnings("unused")
    public void image(String image) {
        getImage().set(image);
        getNeedsPull().set(true);
    }

    @SuppressWarnings("unused")
    public void image(Project other) {
        if (!getProject().equals(other)) {
            getProject().evaluationDependsOn(other.getPath());
        }
        final TaskCollection<ContainerImageProviderTask> tasks = other.getTasks()
                .withType(ContainerImageProviderTask.class)
                // HACK: Projects have an emulated build to work with lock-file updates of dependent projects, so unless
                //       we rule out the emulated one we'll break projects that specified this by project as this will
                //       never work since there will be 2 tasks, but used to work before we made this change.
                //       Sandbox exec will only ever run the non emulated arch, so we can actually pick here, but would
                //       need to make the architecture part of the emulation, that would mean making it more of a first
                //       class citizen. We work around it by going by naming convention here
                .matching(task -> Arrays.stream(Architecture.values()).noneMatch(it -> task.getName().contains(it.dockerName())));

        if (tasks.isEmpty()) {
            throw new GradleException("Can't use image build by " + other.getPath() + " as it doesn't define any " + ContainerImageProviderTask.class.getName());
        }
        if (tasks.size() > 1) {
            throw new GradleException("Can't use image build by " + other.getPath() + " as it defines " +
                                      tasks.size() + " " + ContainerImageProviderTask.class.getName() + ", pass in the required task instead.");
        }

        final ContainerImageProviderTask singleTask = tasks.iterator().next();
        dependsOn(singleTask);
        getImage().set(singleTask.getTag());
        getImageId().set(singleTask.getImageId());
        getNeedsPull().set(false);
    }

    @SuppressWarnings("unused")
    public void image(TaskProvider<? extends ContainerImageProviderTask> task) {
        dependsOn(task);
        //noinspection NullableProblems
        getImage().set(task.flatMap(ContainerImageProviderTask::getTag));
        getNeedsPull().set(false);
    }
}
