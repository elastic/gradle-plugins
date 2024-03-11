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
package co.elastic.gradle.utils.docker;

import co.elastic.gradle.utils.docker.instruction.ContainerImageBuildInstruction;
import co.elastic.gradle.utils.docker.instruction.Copy;
import org.gradle.api.internal.file.copy.CopySpecInternal;
import org.gradle.api.internal.file.copy.DefaultCopySpec;

import java.util.List;

public abstract  class InstructionCopySpecMapper {
    public static void assignCopySpecs(List<ContainerImageBuildInstruction> instructions, DefaultCopySpec rootCopySpec) {
        instructions.stream()
                .filter(each -> each instanceof Copy)
                .map(each -> (Copy) each)
                .forEach(copy -> {
                    final CopySpecInternal childCopySpec = rootCopySpec.addChild();
                    childCopySpec.into(copy.getLayer());
                    // We need another copy spec here, so the `into` from the builds script is to be interpreted as a sub-path
                    // of the layer directory
                    copy.getSpec().execute(childCopySpec.addChild());
                });
    }
}
