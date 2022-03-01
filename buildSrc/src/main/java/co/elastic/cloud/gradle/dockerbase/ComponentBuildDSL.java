package co.elastic.cloud.gradle.dockerbase;

import co.elastic.cloud.gradle.docker.DockerPluginConventions;
import co.elastic.cloud.gradle.util.Architecture;
import kotlin.Pair;
import org.apache.tools.ant.RuntimeConfigurable;
import org.gradle.api.Action;
import org.gradle.api.GradleException;
import org.gradle.api.Project;
import org.gradle.api.file.CopySpec;
import org.gradle.api.internal.file.copy.CopySpecInternal;
import org.gradle.api.internal.file.copy.DefaultCopySpec;

import java.util.ArrayList;
import java.util.List;

public class ComponentBuildDSL {
    final List<JibInstruction> instructions;

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
        otherProject.getPluginManager().apply(DockerBasePlugin.class);
        // We add both local and remote instructions and select the right one when building
        // This does have the undesired side-effect that we will build local images for the current platform, without
        // actually using them but it's not something that we could easily prevent without moving the DSL into a project
        // extension.
        instructions.add(
                new JibInstruction.FromLocalImageBuild(
                        otherProject.getTasks().named(DockerBasePlugin.BUILD_TASK_NAME, BaseBuildTask.class)
                                .flatMap(task -> task.getImageArchive().getAsFile())
                )
        );
        instructions.add(
                new JibInstruction.From(DockerPluginConventions.baseImageTag(
                        otherProject, architecture
                ))
        );
    }

    public void maintainer(String name, String email) {
        instructions.add(
                new JibInstruction.Maintainer(name, email)
        );
    }

    public void copySpec(String owner, Action<CopySpec> copySpecAction) {
        if (task.getState().getExecuting()) {
            throw new GradleException("You cannot add child specs at execution time. Consider configuring this task during configuration time or using a separate task to do the configuration.");
        }

        final String layerName = architecture.name().toLowerCase() +
                "-layer" + instructions.size();

        instructions.add(new JibInstruction.Copy(copySpecAction, ComponentBuildTask.LAYERS_DIR + "/" + layerName, owner));

        final CopySpecInternal childCopySpec = task.rootCopySpec.addChild();
        childCopySpec.into(layerName);
        // We need another copy spec here, so the `into` from the builds script is to be interpreted as a sub-path of the above
        copySpecAction.execute(childCopySpec.addChild());
    }

    public void copySpec(Action<CopySpec> copySpecAction) {
        copySpec(null, copySpecAction);
    }

    public void entryPoint(List<String> entrypoint) {
        instructions.add(new JibInstruction.Entrypoint(entrypoint));
    }

    public void cmd(List<String> cmd) {
        instructions.add(new JibInstruction.Cmd(cmd));
    }

    public void env(Pair<String, String> value) {
        instructions.add(new JibInstruction.Env(value.component1(), value.component2()));
    }

    public void workDir(String dir) {
        instructions.add(new JibInstruction.Workdir(dir));
    }

    public void exposeTcp(Integer port) {
        instructions.add(new JibInstruction.Expose(JibInstruction.Expose.Type.TCP, port));
    }

    public void exposeUdp(Integer port) {
        instructions.add(new JibInstruction.Expose(JibInstruction.Expose.Type.UDP, port));
    }

    public void label(String key, String value) {
        instructions.add(new JibInstruction.Label(key, value));
    }

    public void label(Pair<String, String> value) {
        instructions.add(new JibInstruction.Label(value.component1(), value.component2()));
    }

    public void changingLabel(String key, String value) {
        instructions.add(new JibInstruction.ChangingLabel(key, value));
    }

    public void changingLabel(Pair<String, String> value) {
        instructions.add(new JibInstruction.ChangingLabel(value.component1(), value.component2()));
    }
}