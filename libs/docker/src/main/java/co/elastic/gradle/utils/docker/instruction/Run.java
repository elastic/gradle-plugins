package co.elastic.gradle.utils.docker.instruction;

import org.gradle.api.tasks.*;

import java.nio.file.Path;
import java.util.List;

public class Run implements ContainerImageBuildInstruction {
    private final List<String> commands;
    private final List<BindMount> bindMounts;

    public Run(List<String> commands, List<BindMount> bindMounts) {
        this.commands = commands;
        this.bindMounts = bindMounts;
    }

    @Input
    public List<String> getCommands() {
        return commands;
    }

    @Nested
    public List<BindMount> getBindMounts() {
        return bindMounts;
    }

    public static interface BindMount {
        Path getSource();
        String getTarget();
        boolean isReadWrite();
    }

    /**
     * External bind mounts should be used for source directories that exist or must exist before configuring the bind mount.
     * A common use case would be mounting a directory created by another task.
     */
    public static final class ExternalBindMount implements BindMount {
        private final Path source;
        private final String target;
        private final boolean readWrite;

        public ExternalBindMount(Path source, String target, boolean readWrite) {
            this.source = source;
            this.target = target;
            this.readWrite = readWrite;
        }

        @Override
        @InputDirectory
        @PathSensitive(PathSensitivity.RELATIVE)
        public Path getSource() {
            return source;
        }

        @Override
        @Input
        public String getTarget() {
            return target;
        }

        @Input
        public boolean isReadWrite() {
            return readWrite;
        }
    }

    /**
     * <p>Internal bind mounts are used for source directories that are created internally by the same task and
     * that won't exist when the bind mount is configured.</p>
     *
     * <p>The existing use case is internally generated repositories which must be configured before the task is executed.</p>
     *
     * <p>Please consider the following limitations before deciding to use an internal bind mount:</p>
     * <ol>
     * <li>The source directory is not considered an input and won't be validated</li>
     * <li>The existance of the bind mount must be directly tied to other input for Gradle caching to work as expected</li>
     * </ol>
     */
    public static final class InternalBindMount implements BindMount {
        private final Path source;
        private final String target;
        private final boolean readWrite;

        public InternalBindMount(Path source, String target, boolean readWrite) {
            this.source = source;
            this.target = target;
            this.readWrite = readWrite;
        }

        @Override
        @Internal
        public Path getSource() {
            return source;
        }

        @Override
        @Input
        public String getTarget() {
            return target;
        }

        @Input
        public boolean isReadWrite() {
            return readWrite;
        }
    }
}
