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
package co.elastic.gradle.cli.jfrog;

import co.elastic.gradle.cli.base.BaseCLiExtension;
import co.elastic.gradle.cli.base.BaseCliPlugin;
import co.elastic.gradle.cli.base.CliExtension;
import co.elastic.gradle.utils.Architecture;
import co.elastic.gradle.utils.OS;
import org.gradle.api.GradleException;
import org.gradle.api.Plugin;
import org.gradle.api.Project;
import org.gradle.api.file.ProjectLayout;
import org.gradle.api.provider.ProviderFactory;

import javax.inject.Inject;
import java.io.File;
import java.net.MalformedURLException;
import java.net.URL;
import java.util.Arrays;

public abstract class JFrogPlugin implements Plugin<Project> {

    public static final String EXTENSION_NAME = "jfrog";

    @Override
    public void apply(Project target) {
        target.getPluginManager().apply(BaseCliPlugin.class);
        final BaseCLiExtension extension = target.getExtensions().getByType(CliExtension.class)
                .getExtensions()
                .create(EXTENSION_NAME, BaseCLiExtension.class);
        extension.getVersion().convention("2.16.4");
        extension.getPattern().convention("[organisation]/[module]/v2-jf/[revision]/[module]-[classifier]/jf");
        try {
            extension.getBaseURL().convention(new URL("https://artifactory.elastic.dev/artifactory/jfrog-release-proxy"));
        } catch (MalformedURLException e) {
            throw new IllegalStateException(e);
        }

        target.afterEvaluate(p -> {
            BaseCliPlugin.addDownloadRepo(target, extension);
            Arrays.stream(OS.values()).forEach(os ->
                    Arrays.stream(Architecture.values())
                            .forEach(arch -> {
                                BaseCliPlugin.addDependency(
                                        target,
                                        "artifactory:jfrog-cli:" +
                                        extension.getVersion().get() + ":" +
                                        getKind(os, arch)
                                );
                            })
            );
        });

        target.getTasks().withType(JFrogCliUsingTask.class, t -> {
            t.getJFrogCli().convention(getProjectLayout().file(
                            getProviderFactory().provider(() ->
                                    BaseCliPlugin.getExecutable(target, "jfrog-cli"))
                    )
            );
            t.dependsOn(":" + BaseCliPlugin.SYNC_TASK_NAME);
        });
        target.getTasks().withType(JFrogCliExecTask.class, t -> {
            t.setExecutable(BaseCliPlugin.getExecutable(target, "jfrog-cli"));
            t.dependsOn(":" + BaseCliPlugin.SYNC_TASK_NAME);
        });
    }

    @Inject
    protected abstract ProjectLayout getProjectLayout();

    @Inject
    protected abstract ProviderFactory getProviderFactory();

    @SuppressWarnings("unused")
    public static File getExecutable(Project target) {
        return BaseCliPlugin.getExecutable(target, "jfrog-cli");
    }

    public static File getExecutable(Project target, OS os) {
        return BaseCliPlugin.getExecutable(target, "jfrog-cli", os, Architecture.current());
    }

    private static String getKind(final OS os, final Architecture arch) {
        switch (os) {
            case DARWIN:
                // No arm mac binaries as of this writing
                return "mac-386";
            case LINUX: {
                return switch (arch) {
                    case AARCH64 -> "linux-arm64";
                    case X86_64 -> "linux-amd64";
                };
            }
            default:
                throw new GradleException("Unsupported OS: " + os);
        }
    }
}
