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
import co.elastic.gradle.utils.docker.instruction.ContainerImageBuildInstruction;
import org.gradle.api.artifacts.Configuration;
import org.gradle.api.file.DirectoryProperty;
import org.gradle.api.file.RegularFileProperty;
import org.gradle.api.internal.file.copy.DefaultCopySpec;
import org.gradle.api.provider.ListProperty;
import org.gradle.api.provider.Property;
import org.gradle.api.provider.Provider;
import org.gradle.api.tasks.*;

import java.util.List;

public interface ImageBuildable {
    RegularFileProperty getImageIdFile();

    Property<OSDistribution> getOSDistribution();

    List<ContainerImageBuildInstruction> getActualInstructions();

    DirectoryProperty getWorkingDirectory();

    ListProperty<OsPackageRepository> getMirrorRepositories();

    @Internal
    Provider<String> getImageId();

    @InputFiles
    @PathSensitive(PathSensitivity.RELATIVE )
    Property<Configuration> getDockerEphemeralConfiguration();

    @InputFiles
    @PathSensitive(PathSensitivity.RELATIVE )
    Property<Configuration> getOSPackagesConfiguration();

    DefaultCopySpec getRootCopySpec();

    Property<String> getDockerEphemeralMount();

    Property<Boolean> getIsolateFromExternalRepos();

    @Input
    Property<Architecture> getArchitecture();
}
