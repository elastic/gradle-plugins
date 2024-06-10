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

import co.elastic.gradle.dockerbase.lockfile.BaseLockfile;
import co.elastic.gradle.utils.Architecture;
import co.elastic.gradle.utils.RegularFileUtils;
import co.elastic.gradle.utils.docker.ContainerImageProviderTask;
import co.elastic.gradle.utils.docker.instruction.*;
import kotlin.Pair;
import org.gradle.api.Action;
import org.gradle.api.Project;
import org.gradle.api.file.CopySpec;
import org.gradle.api.file.ProjectLayout;
import org.gradle.api.file.RegularFileProperty;
import org.gradle.api.plugins.ExtensionAware;
import org.gradle.api.provider.*;
import org.gradle.api.tasks.TaskProvider;

import javax.inject.Inject;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.net.URL;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Set;

public abstract class BaseImageExtension implements ExtensionAware {

    private final List<ContainerImageBuildInstruction> instructions = new ArrayList<>();

    public BaseImageExtension() {
        getLockFileLocation().convention(
                getProjectLayout().getProjectDirectory()
                        .file("docker-base-image.lock")
        );

        getPlatforms().convention(
                Set.copyOf(Arrays.stream(Architecture.values()).toList())
        );

        getDockerEphemeralMount().convention("/mnt/ephemeral");

        getMaxOutputSizeMB().convention(-1L);

        getDockerTagPrefix().convention("gradle-docker-base");

        getDockerTagLocalPrefix().convention("local/gradle-docker-base");
    }

    public abstract Property<OSDistribution> getOSDistribution();

    public abstract Property<String> getDockerEphemeralMount();

    public abstract RegularFileProperty getLockFileLocation();

    public Provider<BaseLockfile> getLockFile() {
        return getProviderFactory().provider(() -> {
            try {
                return BaseLockfile.parse(Files.newBufferedReader(RegularFileUtils.toPath(getLockFileLocation())));
            } catch (IOException e) {
                throw new UncheckedIOException("Could not read lockfile", e);
            }
        });
    }

    public abstract SetProperty<Architecture> getPlatforms();

    public abstract Property<Long> getMaxOutputSizeMB();

    public abstract Property<String> getDockerTagPrefix();

    public abstract Property<String> getDockerTagLocalPrefix();

    public abstract ListProperty<OsPackageRepository> getMirrorRepositories();

    public abstract Property<URL> getOsPackageRepository();

    @Inject
    protected abstract ProviderFactory getProviderFactory();

    @Inject
    protected abstract ProjectLayout getProjectLayout();

    //----------------\\
    //  DSL Methods   \\
    //----------------\\

    @SuppressWarnings("unused")
    public String getDockerEphemeral() {
        return getDockerEphemeralMount().get();
    }

    public Architecture getArchitecture() {
        return Architecture.current();
    }

    @SuppressWarnings("unused")
    public void fromUbuntu(String image, String version) {
        getOSDistribution().set(OSDistribution.UBUNTU);
        from(image, version);
        env(new Pair<>("DEBIAN_FRONTEND", "noninteractive"));
    }

    @SuppressWarnings("unused")
    public void fromDebian(String image, String version) {
        getOSDistribution().set(OSDistribution.DEBIAN);
        from(image, version);
        env(new Pair<>("DEBIAN_FRONTEND", "noninteractive"));
    }

    @SuppressWarnings("unused")
    public void fromCentos(String image, String version) {
        getOSDistribution().set(OSDistribution.CENTOS);
        from(image, version);
    }

    private void from(String image, String version) {
        // The sha comes from the lockfile
        instructions.add(new From(getProviderFactory().provider(() -> String.format("%s:%s", image, version))));
    }

    public void from(Project otherProject) {
        final TaskProvider<ContainerImageProviderTask> localImport = otherProject.getTasks()
                .named(DockerBaseImageBuildPlugin.LOCAL_IMPORT_TASK_NAME, ContainerImageProviderTask.class);
        final TaskProvider<DockerBaseImageBuildTask> build = otherProject.getTasks()
                .named(DockerBaseImageBuildPlugin.BUILD_TASK_NAME, DockerBaseImageBuildTask.class);
        instructions.add(new FromLocalImageBuild(
                otherProject.getPath(),
                localImport.flatMap(ContainerImageProviderTask::getTag),
                localImport.flatMap(ContainerImageProviderTask::getImageId)
        ));
        getOSDistribution().set(build.flatMap(DockerBaseImageBuildTask::getOSDistribution));
    }

    public void run(List<String> commands) {
        instructions.add(new Run(commands));
    }

    @SuppressWarnings("unused")
    public void run(String... commands) {
        run(Arrays.asList(commands));
    }

    public void repoConfig(List<String> commands) {
        instructions.add(new RepoConfigRun(commands));
    }

    @SuppressWarnings("unused")
    public void repoConfig(String... commands) {
        repoConfig(Arrays.asList(commands));
    }

    public void repoInstall(List<String> packages) {
        instructions.add(new RepoConfigInstall(packages));
    }

    @SuppressWarnings("unused")
    public void repoInstall(String... packages) {
        repoInstall(Arrays.asList(packages));
    }

    @SuppressWarnings("unused")
    public void createUser(String username, Integer userId, String group, Integer groupId) {
        instructions.add(new CreateUser(username, group, userId, groupId));
    }

    @SuppressWarnings("unused")
    public void setUser(String username) {
        instructions.add(new SetUser(username));
    }

    @SuppressWarnings("unused")
    public void env(Pair<String, String> value) {
        instructions.add(new Env(value.component1(), value.component2()));
    }

    @SuppressWarnings("unused")
    public void install(String... packages) {
        instructions.add(new Install(Arrays.asList(packages)));
    }

    @SuppressWarnings("unused")
    public void healthcheck(String cmd) {
        healthcheck(cmd, null, null, null, null);
    }

    @SuppressWarnings("unused")
    public void healthcheck(String cmd, String interval, String timeout, String startPeriod, Integer retries) {
        instructions.add(new HealthCheck(cmd, interval, timeout, startPeriod, retries));
    }

    public void copySpec(String owner, Action<CopySpec> copySpecAction) {
        final String layerName = "layer" + instructions.size();
        instructions.add(new Copy(
                copySpecAction,
                layerName,
                owner
        ));
    }

    @SuppressWarnings("unused")
    public void copySpec(Action<CopySpec> copySpec) {
        copySpec(null, copySpec);
    }

    public List<ContainerImageBuildInstruction> getInstructions() {
        return instructions;
    }

}
