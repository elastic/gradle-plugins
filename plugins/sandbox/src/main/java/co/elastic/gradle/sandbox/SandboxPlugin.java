package co.elastic.gradle.sandbox;

import co.elastic.gradle.lifecycle.LifecyclePlugin;
import org.gradle.api.Plugin;
import org.gradle.api.Project;
import org.gradle.api.provider.ProviderFactory;
import org.gradle.api.tasks.TaskProvider;

import javax.inject.Inject;
import java.util.stream.Collectors;

public abstract class SandboxPlugin implements Plugin<Project> {

    @Inject
    public abstract ProviderFactory getProviderFactory();

    @Override
    public void apply(Project target) {
        final TaskProvider<DockerImagePull> resolveSandboxDockerDependencies = target.getTasks().register(
                "resolveSandboxDockerDependencies", DockerImagePull.class,
                t -> t.getTags().set(
                        getProviderFactory().provider(() ->
                                target.getTasks().withType(SandboxDockerExecTask.class)
                                        .stream()
                                        .filter(each -> each.getNeedsPull().get())
                                        .map(each -> each.getImage().get())
                                        .collect(Collectors.toList())
                        )
                )
        );
        target.getTasks().withType(SandboxDockerExecTask.class).configureEach(task ->
                task.dependsOn(resolveSandboxDockerDependencies)
        );

        LifecyclePlugin.resolveAllDependencies(target, resolveSandboxDockerDependencies);
    }
}
