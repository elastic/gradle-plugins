package co.elastic.gradle.lifecycle;

import org.gradle.api.DefaultTask;
import org.gradle.api.artifacts.Configuration;
import org.gradle.api.file.ProjectLayout;
import org.gradle.api.file.RegularFileProperty;
import org.gradle.api.tasks.*;

import javax.inject.Inject;
import java.io.IOException;
import java.nio.file.Files;
import java.util.Set;
import java.util.stream.Collectors;

public abstract class ResolveAllDependenciesTask extends DefaultTask {

    public ResolveAllDependenciesTask() {
        setDescription("Lifecycle task to resolves all external dependencies. " +
                            "This task can be used to cache everything locally so these are not  downloaded while building." +
                            " e.g. to download everything while on a better connection. In CI this is used to \"bake\" depdencies " +
                            "so they are not re-downloaded on every run."
        );
        setGroup("prepare");
        getMarkerFile().convention(
                getProjectLayout().getBuildDirectory().file(getName() + ".marker")
        );
    }

    @Inject
    protected abstract ProjectLayout getProjectLayout();

    @OutputFile
    public abstract RegularFileProperty getMarkerFile();

    @InputFiles
    @PathSensitive(PathSensitivity.NONE)
    public Set<Configuration> getResolvableConfigurations() {
        return getProject().getConfigurations().stream()
                .filter(Configuration::isCanBeResolved)
                .collect(Collectors.toSet());
    }

    @TaskAction
    public void resolveConfigurations() throws IOException {
        final Set<Configuration> resolvableConfigurations = getResolvableConfigurations();
        resolvableConfigurations.forEach(Configuration::resolve);
        Files.writeString(
                getMarkerFile().get().getAsFile().toPath(),
                "Resolved configurations:" + resolvableConfigurations.size()
        );
    }

}
