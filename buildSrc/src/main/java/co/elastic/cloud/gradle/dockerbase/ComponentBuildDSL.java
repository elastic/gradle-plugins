package co.elastic.cloud.gradle.dockerbase;

import co.elastic.cloud.gradle.docker.DockerBuildContext;
import co.elastic.cloud.gradle.util.Architecture;
import co.elastic.cloud.gradle.util.OS;
import kotlin.Pair;
import org.gradle.api.Action;
import org.gradle.api.file.CopySpec;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

public class ComponentBuildDSL {
    final List<JibInstruction> instructions;

    private final OS os;
    private final Architecture architecture;
    private final ComponentBuildTask task;


    public ComponentBuildDSL(ComponentBuildTask task, OS os, Architecture architecture) {
        this.os = os;
        this.architecture = architecture;
        this.task = task;
        instructions = new ArrayList<>();
    }

    public OS getOs() {
        return os;
    }

    public Architecture getArchitecture() {
        return architecture;
    }

    public void maintainer(String name, String email) {
        instructions.add(
                new JibInstruction.Maintainer(name, email)
        );
    }

    public void copySpec(String owner, Action<CopySpec> copySpecAction) {
        final String layerName = os.name().toLowerCase(Locale.ROOT) + "_" +
                architecture.name().toLowerCase() +
                "-layer" + instructions.size();

        instructions.add(new JibInstruction.Copy(copySpecAction, DockerBuildContext.CONTEXT_FOLDER + "/" + layerName, owner));
        // This is an intersection point between Gradle and Docker so we need to instruct Gradle to create the layers
        // since Docker doesn't understand copySpecs.
        // This links the copy specs from the DSL together and is conceptually like adding an `into("layerX")`
        // to the original copy spec
        task.with(
                task.getProject().copySpec(child -> {
                    child.into(layerName);
                    CopySpec dslSpec = task.getProject().copySpec();
                    copySpecAction.execute(dslSpec);
                    child.with(dslSpec);
                })
        );
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