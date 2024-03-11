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
package co.elastic.gradle.cli.manifest;

import co.elastic.gradle.cli.base.BaseCLiExtension;
import co.elastic.gradle.cli.base.BaseCliPlugin;
import co.elastic.gradle.cli.base.CliExtension;
import co.elastic.gradle.utils.Architecture;
import co.elastic.gradle.utils.OS;
import co.elastic.gradle.utils.PrefixingOutputStream;
import org.gradle.api.Plugin;
import org.gradle.api.Project;

import java.io.File;
import java.util.Arrays;

import java.util.Collections;

public class ManifestToolPlugin implements Plugin<Project> {

    public static File getExecutable(Project target) {
        return BaseCliPlugin.getExecutable(target, "manifest-tool");
    }

    @Override
    public void apply(Project target) {
        target.getPluginManager().apply(BaseCliPlugin.class);

        final BaseCLiExtension extension = target.getExtensions().getByType(CliExtension.class)
                .getExtensions()
                .create("manifestTool", BaseCLiExtension.class);
        extension.getVersion().convention("v2.0.3");

        target.afterEvaluate(p -> {
            BaseCliPlugin.addDownloadRepo(target, extension);
            if (extension.getVersion().get().startsWith("v1.")) {
                Arrays.stream(OS.values()).forEach(os ->
                        Arrays.stream(Architecture.values())
                                .filter(arch -> !(OS.current().equals(OS.DARWIN) && arch.equals(Architecture.AARCH64)))
                                .forEach(arch -> {
                                            BaseCliPlugin.addDependency(
                                                    target,
                                                    "estesp/manifest-tool:manifest-tool:" +
                                                    extension.getVersion().get() + ":" +
                                                    os.name().toLowerCase() + "-" +
                                                    arch.dockerName()
                                            );
                                        }
                                )
                );
            } else {
                extension.getPattern().convention("[organisation]/releases/download/[revision]/[module].[classifier]");
                BaseCliPlugin.addDependency(
                        target,
                        "estesp/manifest-tool:binaries-manifest-tool-" +
                        extension.getVersion().get().substring(1) + ":" + extension.getVersion().get() + ":tar.gz"
                );
            }
        });

        target.getTasks().withType(ManifestToolExecTask.class)
                .configureEach(task -> {
                    task.setEnvironment(Collections.emptyMap());
                    task.setExecutable(getExecutable(target));
                    task.dependsOn(":" + BaseCliPlugin.SYNC_TASK_NAME);
                    task.setStandardOutput(new PrefixingOutputStream("[manifest-tool] ", System.out));
                    task.setErrorOutput(new PrefixingOutputStream("[manifest-tool] ", System.err));
                });
    }


}
