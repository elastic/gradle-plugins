package co.elastic.gradle.dockerbase;

import co.elastic.gradle.utils.docker.instruction.ContainerImageBuildInstruction;
import co.elastic.gradle.utils.docker.instruction.Copy;
import org.gradle.api.artifacts.Configuration;
import org.gradle.api.file.DirectoryProperty;
import org.gradle.api.file.RegularFileProperty;
import org.gradle.api.internal.file.copy.CopySpecInternal;
import org.gradle.api.internal.file.copy.DefaultCopySpec;
import org.gradle.api.provider.ListProperty;
import org.gradle.api.provider.Property;

import java.util.List;

public interface ImageBuildable {
    RegularFileProperty getImageIdFile();

    Property<OSDistribution> getOSDistribution();

    List<ContainerImageBuildInstruction> getActualInstructions();

    DirectoryProperty getWorkingDirectory();

    ListProperty<OsPackageRepository> getMirrorRepositories();

    Configuration getDockerEphemeralConfiguration();

    DefaultCopySpec getRootCopySpec();

    Property<String> getDockerEphemeralMount();

    static void assignCopySpecs(List<ContainerImageBuildInstruction> instructions, ImageBuildable buildable) {
        instructions.stream()
                .filter(each -> each instanceof Copy)
                .map(each -> (Copy) each)
                .forEach(copy -> {
                    final CopySpecInternal childCopySpec = buildable.getRootCopySpec().addChild();
                    childCopySpec.into(copy.getLayer());
                    // We need another copy spec here, so the `into` from the builds script is to be interpreted as a sub-path
                    // of the layer directory
                    copy.getSpec().execute(childCopySpec.addChild());
                });
    }
}
