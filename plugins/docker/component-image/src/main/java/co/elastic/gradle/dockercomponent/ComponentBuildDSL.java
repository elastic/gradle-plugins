package co.elastic.gradle.dockercomponent;

import co.elastic.gradle.utils.Architecture;
import co.elastic.gradle.utils.docker.ContainerArchiveProviderTask;
import co.elastic.gradle.utils.docker.instruction.*;
import kotlin.Pair;
import org.gradle.api.Action;
import org.gradle.api.GradleException;
import org.gradle.api.Project;
import org.gradle.api.file.CopySpec;
import org.gradle.api.internal.file.copy.CopySpecInternal;

import java.util.ArrayList;
import java.util.List;

public class ComponentBuildDSL {
    final List<ContainerImageBuildInstruction> instructions;

    private final Architecture architecture;
    private final ComponentBuildTask task;


    public ComponentBuildDSL(ComponentBuildTask task, Architecture architecture) {
        this.architecture = architecture;
        this.task = task;
        instructions = new ArrayList<>();
    }

    public Architecture getArchitecture() {
        return architecture;
    }

    public void from(Project otherProject) {
        // We add both local and remote instructions and select the right one when building
        // This does have the undesired side-effect that we will build local images for the current platform, without
        // actually using them but it's not something that we could easily prevent without moving the DSL into a project
        // extension.
        instructions.add(
                new FromLocalArchive(
                        otherProject.getTasks().named("dockerBaseImageBuild", ContainerArchiveProviderTask.class)
                                .flatMap(task -> task.getImageArchive().getAsFile())
                )
        );
        final String[] image = (otherProject.getName() + "-" + architecture.dockerName() +
                                ":" + otherProject.getVersion()).split(":");
        instructions.add(
                new From(image[0], image[1], null)
        );
    }

    public void maintainer(String name, String email) {
        instructions.add(
                new Maintainer(name, email)
        );
    }

    public void copySpec(String owner, Action<CopySpec> copySpecAction) {
        if (task.getState().getExecuting()) {
            throw new GradleException("You cannot add child specs at execution time. Consider configuring this task during configuration time or using a separate task to do the configuration.");
        }

        final String layerName = architecture.name().toLowerCase() +
                "-layer" + instructions.size();

        instructions.add(new Copy(copySpecAction, ComponentBuildTask.LAYERS_DIR + "/" + layerName, owner));

        final CopySpecInternal childCopySpec = task.rootCopySpec.addChild();
        childCopySpec.into(layerName);
        // We need another copy spec here, so the `into` from the builds script is to be interpreted as a sub-path of the above
        copySpecAction.execute(childCopySpec.addChild());
    }

    public void copySpec(Action<CopySpec> copySpecAction) {
        copySpec(null, copySpecAction);
    }

    public void entryPoint(List<String> entrypoint) {
        instructions.add(new Entrypoint(entrypoint));
    }

    public void cmd(List<String> cmd) {
        instructions.add(new Cmd(cmd));
    }

    public void env(Pair<String, String> value) {
        instructions.add(new Env(value.component1(), value.component2()));
    }

    public void workDir(String dir) {
        instructions.add(new Workdir(dir));
    }

    public void exposeTcp(Integer port) {
        instructions.add(new Expose(Expose.Type.TCP, port));
    }

    public void exposeUdp(Integer port) {
        instructions.add(new Expose(Expose.Type.UDP, port));
    }

    public void label(String key, String value) {
        instructions.add(new Label(key, value));
    }

    public void label(Pair<String, String> value) {
        instructions.add(new Label(value.component1(), value.component2()));
    }

    public void changingLabel(String key, String value) {
        instructions.add(new ChangingLabel(key, value));
    }

    public void changingLabel(Pair<String, String> value) {
        instructions.add(new ChangingLabel(value.component1(), value.component2()));
    }
}