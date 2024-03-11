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

import co.elastic.gradle.utils.Architecture;
import co.elastic.gradle.utils.docker.instruction.ContainerImageBuildInstruction;
import org.gradle.api.Action;
import org.gradle.api.file.ProjectLayout;
import org.gradle.api.file.RegularFileProperty;
import org.gradle.api.plugins.ExtensionAware;
import org.gradle.api.provider.MapProperty;
import org.gradle.api.provider.Property;
import org.gradle.api.provider.ProviderFactory;

import javax.inject.Inject;
import java.util.*;
import java.util.function.Function;
import java.util.stream.Collectors;

public abstract  class ComponentImageBuildExtension implements ExtensionAware {

    private final List<Action<ComponentBuildDSL>> actions = new ArrayList<>();
    private final Set<Architecture> platforms = new HashSet<>();

    public ComponentImageBuildExtension() {
        getInstructions().set(
                getProviderFactory().provider(
                        () -> platforms
                                 .stream()
                                 .collect(Collectors.toMap(
                                         Function.identity(),
                                         platform -> actions.stream()
                                                 .flatMap(dslAction -> {
                                                             final ComponentBuildDSL dsl = new ComponentBuildDSL(platform, getProviderFactory());
                                                             dslAction.execute(dsl);
                                                             return dsl.getInstructions().stream();
                                                         }
                                                 )
                                                 .toList()
                                 ))
                )
        );

        getDockerTagPrefix().convention("gradle-docker-component");

        getDockerTagLocalPrefix().convention("local/gradle-docker-component");

        getLockFileLocation().convention(
                getProjectLayout().getProjectDirectory()
                        .file("docker-component-image.lock")
        );


        getMaxOutputSizeMB().convention(-1L);
    }

    public abstract Property<Long> getMaxOutputSizeMB();

    @Inject
    protected abstract ProjectLayout getProjectLayout();

    protected abstract RegularFileProperty getLockFileLocation();

    public abstract Property<String> getDockerTagPrefix();

    public abstract Property<String> getDockerTagLocalPrefix();

    @Inject
    protected abstract ProviderFactory getProviderFactory();


    public abstract MapProperty<Architecture, List<ContainerImageBuildInstruction>> getInstructions();

    public void buildOnly(List<Architecture> platformList, Action<ComponentBuildDSL> action) {
        platforms.addAll(platformList);
        actions.add(action);
    }

    public void buildAll(Action<ComponentBuildDSL> action) {
        buildOnly(Arrays.asList(Architecture.values()), action);
    }

    public void configure(Action<ComponentBuildDSL> action) {
        actions.add(action);
    }

}
