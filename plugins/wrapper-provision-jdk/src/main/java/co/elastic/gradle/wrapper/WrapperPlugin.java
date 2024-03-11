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
package co.elastic.gradle.wrapper;

import org.gradle.api.Plugin;
import org.gradle.api.Project;
import org.gradle.api.tasks.TaskProvider;
import org.gradle.api.tasks.wrapper.Wrapper;

public class WrapperPlugin implements Plugin<Project> {
    @Override
    public void apply(Project target) {
        target.getPluginManager().apply(org.gradle.buildinit.plugins.WrapperPlugin.class);
        if (target.getParent() == null) {
            final TaskProvider<ProvisionJDKInWrapperTask> provisionJdk = target.getTasks()
                    .register("wrapperProvisionJdk", ProvisionJDKInWrapperTask.class, task -> {
                        task.setGroup("Build Setup");
                        task.setDescription("Generates Gradle wrapper files.");
                        task.dependsOn("wrapper");
                    });
            target.getTasks().withType(Wrapper.class, wrapper -> {
                wrapper.finalizedBy(provisionJdk);
            });
        }
    }
}
