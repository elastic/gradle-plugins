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
package co.elastic.gradle.cli.shellcheck;

import co.elastic.gradle.cli.base.BaseCLiExtension;
import co.elastic.gradle.cli.base.BaseCliPlugin;
import co.elastic.gradle.cli.base.CliExtension;
import co.elastic.gradle.lifecycle.LifecyclePlugin;
import co.elastic.gradle.lifecycle.MultiArchLifecyclePlugin;
import co.elastic.gradle.utils.Architecture;
import co.elastic.gradle.utils.OS;
import org.gradle.api.Plugin;
import org.gradle.api.Project;
import org.gradle.api.file.FileCollection;
import org.gradle.api.tasks.TaskProvider;

import java.nio.file.Files;
import java.util.Arrays;
import java.util.Locale;

@SuppressWarnings("unused")
public class ShellcheckPlugin implements Plugin<Project> {

    @Override
    public void apply(Project target) {
        target.getPluginManager().apply(BaseCliPlugin.class);
        final BaseCLiExtension extension = target.getExtensions().getByType(CliExtension.class)
                .getExtensions()
                .create("shellcheck", BaseCLiExtension.class);
        extension.getVersion().convention("v0.10.0");

        extension.getPattern()
                .convention("[organisation]/releases/download/[revision]/[module]-[revision].[classifier]");

        target.afterEvaluate(p -> {
            BaseCliPlugin.addDownloadRepo(target, extension);
            Arrays.stream(OS.values()).forEach(os ->
                    Arrays.stream(Architecture.values())
                            .forEach(arch -> {
                                        BaseCliPlugin.addDependency(
                                                target,
                                                "koalaman/shellcheck:shellcheck:" +
                                                extension.getVersion().get() + ":" +
                                                os.name().toLowerCase() + "." +
                                                arch.name().toLowerCase(Locale.ROOT) + ".tar.xz"
                                        );
                                    }
                            )
            );
        });

        final TaskProvider<ShellcheckTask> shellcheck = target.getTasks().register(
                "shellcheck",
                ShellcheckTask.class
        );
        if (Files.exists(target.getProjectDir().toPath().resolve("src"))) {
            shellcheck.configure(task -> task.check((FileCollection) target.fileTree("src").include("**/*.sh")));
        }

        target.getTasks().withType(ShellcheckTask.class).configureEach( task -> {
            task.getTool().set(BaseCliPlugin.getExecutable(target, "shellcheck"));
            task.dependsOn(":" + BaseCliPlugin.SYNC_TASK_NAME);
        });

        MultiArchLifecyclePlugin.checkPlatformIndependent(target, shellcheck);
        LifecyclePlugin.check(target, shellcheck);
    }

}
