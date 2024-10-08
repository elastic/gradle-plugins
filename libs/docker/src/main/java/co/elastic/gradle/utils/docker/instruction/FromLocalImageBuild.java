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
package co.elastic.gradle.utils.docker.instruction;

import co.elastic.gradle.utils.Architecture;
import org.gradle.api.provider.Provider;
import org.gradle.api.tasks.Input;
import org.gradle.api.tasks.Internal;

public record FromLocalImageBuild(String otherProjectPath,
                                  Provider<String> tag,
                                  Provider<String> imageId,
                                  Architecture architecture
    ) implements FromImageReference {


    @Input
    public Provider<String> getImageId() {
        return imageId;
    }

    @Internal
    // This is a quick fix to add this here and be able to filter based on it.
    // Ideally now that we added emulation in a number of places, we should have a Map<Architecture, List<Instruction>>
    // for the base plugin, similar to the component plugin
    public Architecture getArchitecture() { return architecture ;}

    @Override
    @Internal
    public Provider<String> getReference() {
        return tag;
    }
}