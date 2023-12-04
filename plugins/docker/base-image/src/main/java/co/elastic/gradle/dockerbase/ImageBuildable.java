package co.elastic.gradle.dockerbase;

import co.elastic.gradle.utils.docker.instruction.ContainerImageBuildInstruction;
import org.gradle.api.artifacts.Configuration;
import org.gradle.api.file.DirectoryProperty;
import org.gradle.api.file.RegularFileProperty;
import org.gradle.api.internal.file.copy.DefaultCopySpec;
import org.gradle.api.provider.ListProperty;
import org.gradle.api.provider.Property;
import org.gradle.api.provider.Provider;
import org.gradle.api.tasks.InputFiles;
import org.gradle.api.tasks.Internal;
import org.gradle.api.tasks.PathSensitive;
import org.gradle.api.tasks.PathSensitivity;

import java.util.List;

public interface ImageBuildable {
    RegularFileProperty getImageIdFile();

    Property<OSDistribution> getOSDistribution();

    List<ContainerImageBuildInstruction> getActualInstructions();

    DirectoryProperty getWorkingDirectory();

    ListProperty<OsPackageRepository> getMirrorRepositories();

    @Internal
    Provider<String> getImageId();

    @InputFiles
    @PathSensitive(PathSensitivity.RELATIVE )
    Property<Configuration> getDockerEphemeralConfiguration();

    @InputFiles
    @PathSensitive(PathSensitivity.RELATIVE )
    Property<Configuration> getOSPackagesConfiguration();

    DefaultCopySpec getRootCopySpec();

    Property<String> getDockerEphemeralMount();

    Property<Boolean> getRequiresCleanLayers();

    Property<Boolean> getOnlyUseMirrorRepositories();
}
