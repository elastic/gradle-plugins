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
package co.elastic.gradle.cig;

import org.gradle.api.Plugin;
import org.gradle.api.Project;
import org.gradle.api.tasks.TaskProvider;

public class CheckInGeneratedPlugin implements Plugin<Project> {

    @Override
    public void apply(Project target) {
        final CheckInGeneratedExtension extension = target.getExtensions()
                .create("checkInGenerated", CheckInGeneratedExtension.class);

        target.getTasks().register("generate", CopyFileMapTask.class, copy -> {
           copy.getMap().set(extension.getMap());
           copy.dependsOn(extension.getGeneratorTask());
           copy.setDescription("Regenerates the checked in generated resources");
           copy.setGroup("generate");
        });

        TaskProvider<CompareFileMapTask> verifyGenerated = target.getTasks().register("verifyGenerated", CompareFileMapTask.class, compare -> {
            compare.setGroup("generate");
            compare.setDescription("Verify that the checked in resources match what would be generated right now");
            compare.getMap().set(extension.getMap());
        });

        target.afterEvaluate( p -> {
            extension.getGeneratorTask().get().configure(generator -> {
                // Make sure we always generate after verification, otherwise there's mo point to verify
                generator.mustRunAfter(verifyGenerated);
            });
        });

    }

}
