package co.elastic.gradle.utils.docker;

import co.elastic.gradle.utils.docker.instruction.ContainerImageBuildInstruction;
import co.elastic.gradle.utils.docker.instruction.Copy;
import org.gradle.api.internal.file.copy.CopySpecInternal;
import org.gradle.api.internal.file.copy.DefaultCopySpec;

import java.util.List;

public abstract  class InstructionCopySpecMapper {
    public static void assignCopySpecs(List<ContainerImageBuildInstruction> instructions, DefaultCopySpec rootCopySpec) {
        instructions.stream()
                .filter(each -> each instanceof Copy)
                .map(each -> (Copy) each)
                .forEach(copy -> {
                    final CopySpecInternal childCopySpec = rootCopySpec.addChild();
                    childCopySpec.into(copy.getLayer());
                    // We need another copy spec here, so the `into` from the builds script is to be interpreted as a sub-path
                    // of the layer directory
                    copy.getSpec().execute(childCopySpec.addChild());
                });
    }
}
