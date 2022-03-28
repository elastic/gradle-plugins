package co.elastic.gradle.utils.docker;

import org.gradle.api.Task;
import org.gradle.api.file.RegularFileProperty;

public interface ContainerArchiveProviderTask extends Task {

    RegularFileProperty getImageArchive();

}
