package co.elastic.gradle.utils.docker.instruction;

import org.gradle.api.provider.Provider;
import org.gradle.api.tasks.InputFile;
import org.gradle.api.tasks.PathSensitive;
import org.gradle.api.tasks.PathSensitivity;

import java.io.File;

public class FromLocalArchive implements ContainerImageBuildInstruction {

        private final Provider<File> baseImage;

        public FromLocalArchive(Provider<File> baseImage) {
            this.baseImage = baseImage;
        }

        @InputFile
        @PathSensitive(PathSensitivity.RELATIVE)
        public Provider<File> getImageArchive() {
            return baseImage;
        }

}
