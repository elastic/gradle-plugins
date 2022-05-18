package co.elastic.gradle.cli.jfrog;

import org.gradle.api.Task;
import org.gradle.api.file.RegularFileProperty;
import org.gradle.api.tasks.InputFile;
import org.gradle.api.tasks.PathSensitive;
import org.gradle.api.tasks.PathSensitivity;

public interface JFrogCliUsingTask extends Task {

    @InputFile
    @PathSensitive(PathSensitivity.NAME_ONLY)
    RegularFileProperty getJFrogCli();

}
