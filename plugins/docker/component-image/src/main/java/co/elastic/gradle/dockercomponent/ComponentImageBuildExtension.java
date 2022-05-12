package co.elastic.gradle.dockercomponent;

import co.elastic.gradle.utils.Architecture;
import co.elastic.gradle.utils.docker.instruction.ContainerImageBuildInstruction;
import org.gradle.api.Action;
import org.gradle.api.file.ProjectLayout;
import org.gradle.api.file.RegularFileProperty;
import org.gradle.api.plugins.ExtensionAware;
import org.gradle.api.provider.MapProperty;
import org.gradle.api.provider.Property;
import org.gradle.api.provider.ProviderFactory;

import javax.inject.Inject;
import java.util.*;
import java.util.function.Function;
import java.util.stream.Collectors;

public abstract  class ComponentImageBuildExtension implements ExtensionAware {

    private final List<Action<ComponentBuildDSL>> actions = new ArrayList<>();
    private final Set<Architecture> platforms = new HashSet<>();

    public ComponentImageBuildExtension() {
        getInstructions().set(
                getProviderFactory().provider(
                        () -> platforms
                                 .stream()
                                 .collect(Collectors.toMap(
                                         Function.identity(),
                                         platform -> actions.stream()
                                                 .flatMap(dslAction -> {
                                                             final ComponentBuildDSL dsl = new ComponentBuildDSL(platform, getProviderFactory());
                                                             dslAction.execute(dsl);
                                                             return dsl.getInstructions().stream();
                                                         }
                                                 )
                                                 .toList()
                                 ))
                )
        );

        getDockerTagPrefix().convention("gradle-docker-component");

        getDockerTagLocalPrefix().convention("local/gradle-docker-component");

        getLockFileLocation().convention(
                getProjectLayout().getProjectDirectory()
                        .file("docker-component-image.lock")
        );


        getMaxOutputSizeMB().convention(-1L);
    }

    public abstract Property<Long> getMaxOutputSizeMB();

    @Inject
    protected abstract ProjectLayout getProjectLayout();

    protected abstract RegularFileProperty getLockFileLocation();

    public abstract Property<String> getDockerTagPrefix();

    public abstract Property<String> getDockerTagLocalPrefix();

    @Inject
    protected abstract ProviderFactory getProviderFactory();


    public abstract MapProperty<Architecture, List<ContainerImageBuildInstruction>> getInstructions();

    public void buildOnly(List<Architecture> platformList, Action<ComponentBuildDSL> action) {
        platforms.addAll(platformList);
        actions.add(action);
    }

    public void buildAll(Action<ComponentBuildDSL> action) {
        buildOnly(Arrays.asList(Architecture.values()), action);
    }

    public void configure(Action<ComponentBuildDSL> action) {
        actions.add(action);
    }

}
