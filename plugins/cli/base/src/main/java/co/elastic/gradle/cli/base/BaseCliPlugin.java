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
package co.elastic.gradle.cli.base;

import co.elastic.gradle.lifecycle.LifecyclePlugin;
import co.elastic.gradle.utils.Architecture;
import co.elastic.gradle.utils.OS;
import org.gradle.api.Action;
import org.gradle.api.Plugin;
import org.gradle.api.Project;
import org.gradle.api.artifacts.Configuration;
import org.gradle.api.artifacts.dsl.DependencyHandler;
import org.gradle.api.artifacts.repositories.IvyArtifactRepository;
import org.gradle.api.artifacts.repositories.PasswordCredentials;
import org.gradle.api.artifacts.type.ArtifactTypeDefinition;
import org.gradle.api.file.ProjectLayout;
import org.gradle.api.provider.ProviderFactory;
import org.gradle.api.tasks.TaskProvider;

import javax.inject.Inject;
import java.io.File;
import java.net.URL;
import java.nio.file.Path;
import java.util.Locale;


public abstract class BaseCliPlugin implements Plugin<Project> {

    public static final String CONFIGURATION_NAME = "static-cli";
    public static final String SYNC_TASK_NAME = "syncBinDirStaticCli";

    @Inject
    protected abstract ProjectLayout getProjectLayout();

    @Inject
    protected abstract ProviderFactory getProviderFactory();

    @Override
    public void apply(Project target) {
        target.getExtensions().create("cli", CliExtension.class);

        if (target.getParent() != null) {
            // We only add configuration here to the root project so that we provision the tools only once
            target.getRootProject().getPluginManager().apply(BaseCliPlugin.class);
            return;
        }

        final Configuration configuration = target.getConfigurations().create(CONFIGURATION_NAME);
        final DependencyHandler dependencies = target.getDependencies();

        dependencies.registerTransform(ExtractAndSetExecutableTransform.class, config -> {
            config.getFrom().attribute(ArtifactTypeDefinition.ARTIFACT_TYPE_ATTRIBUTE, "transform");
            config.getTo().attribute(ArtifactTypeDefinition.ARTIFACT_TYPE_ATTRIBUTE, "transform-extracted");
        });
        configuration.getAttributes().attribute(ArtifactTypeDefinition.ARTIFACT_TYPE_ATTRIBUTE, "transform-extracted");

        final TaskProvider<MultipleSymlinkTask> syncBinDirStaticCli = target.getTasks().register(
                SYNC_TASK_NAME,
                MultipleSymlinkTask.class,
                task -> task.dependsOn(configuration)
        );

        LifecyclePlugin.syncBinDir(target, syncBinDirStaticCli);
    }

    public static void addDownloadRepo(Project target, BaseCLiExtension extension) {
        target.afterEvaluate(p -> {
            URL url = extension.getBaseURL().get();

            Action<? super PasswordCredentials> credentialsAction =
            (extension.getUsername().isPresent() && extension.getPassword().isPresent()) ?
                config -> {
                    config.setUsername(extension.getUsername().get());
                    config.setPassword(extension.getPassword().get());
                } : null;

            target.getRootProject().getRepositories().ivy(repo -> {
                repo.setName(url.getHost() + "/" + url.getPath());
                repo.metadataSources(IvyArtifactRepository.MetadataSources::artifact);
                repo.setUrl(url);
                // We don't use [ext] and add extension to classifier instead since Gradle doesn't allow it to be empty and defaults to jar
                repo.patternLayout(config1 -> config1.artifact(extension.getPattern().get()));
                repo.content(content -> content.onlyForConfigurations(BaseCliPlugin.CONFIGURATION_NAME));
                if (credentialsAction != null) {
                    repo.credentials(credentialsAction);
                }
            });
        });
    }

    private static Path getPathToSyncedBinary(Project target, String name) {
        return target.getRootDir().toPath().resolve("gradle/bin").resolve(name);
    }

    @SuppressWarnings("unused")
    public static void addDependency(Project project, String dependencyNotation) {
        project.getRootProject().getDependencies().add(
                CONFIGURATION_NAME,
                dependencyNotation + "@transform"
        );
    }

    public static File getExecutable(
            Project target,
            String artefactName,
            OS os,
            Architecture architecture
    ) {
        return getPathToSyncedBinary(
                target.getRootProject(),
                artefactName +
                "-" + os.name().toLowerCase(Locale.ROOT) +
                "-" + architecture.name().toLowerCase(Locale.ROOT)
        ).toFile();
    }


    public static File getExecutable(Project target, String artefactName) {
        return getPathToSyncedBinary(
                target.getRootProject(),
                artefactName
        ).toFile();
    }



}
