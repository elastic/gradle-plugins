package co.elastic.gradle.cli.base;

import co.elastic.gradle.lifecycle.LifecyclePlugin;
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


public abstract class BaseCliPlugin implements Plugin<Project> {

    public static final String CONFIGURATION_NAME = "static-cli";
    public static final String SYNC_TASK_NAME = "syncBinDirStaticCli";

    @Inject
    protected abstract ProjectLayout getProjectLayout();

    @Inject
    protected abstract ProviderFactory getProviderFactory();

    @Override
    public void apply(Project target) {
        if (target.getParent() != null) {
            // We only add configuration here to the root project so that we provision the tools only once
            target.getRootProject().getPluginManager().apply(BaseCliPlugin.class);
            return;
        }

        final CliExtension cli = target.getExtensions().create("cli", CliExtension.class);

        final Configuration configuration = target.getConfigurations().create(CONFIGURATION_NAME);
        final DependencyHandler dependencies = target.getDependencies();

        dependencies.registerTransform(ExtractAndSetExecutableTransform.class, config -> {
            config.getFrom().attribute(ArtifactTypeDefinition.ARTIFACT_TYPE_ATTRIBUTE, "transform");
            config.getTo().attribute(ArtifactTypeDefinition.ARTIFACT_TYPE_ATTRIBUTE, "transform-extracted");
        });
        configuration.getAttributes().attribute(ArtifactTypeDefinition.ARTIFACT_TYPE_ATTRIBUTE, "transform-extracted");

        final TaskProvider<MultipleSymlinkTask> syncBinDirStaticCli = target.getTasks().register(SYNC_TASK_NAME, MultipleSymlinkTask.class);

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

    public static File getExecutable(Project target, String artefactName) {
        final Project rootProject = target.getRootProject();
        return getPathToSyncedBinary(rootProject, artefactName).toFile();
    }

}
