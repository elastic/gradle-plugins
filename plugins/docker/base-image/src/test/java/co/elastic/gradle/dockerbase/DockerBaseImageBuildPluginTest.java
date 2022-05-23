package co.elastic.gradle.dockerbase;

import org.gradle.api.Project;
import org.gradle.testfixtures.ProjectBuilder;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;

class DockerBaseImageBuildPluginTest {

    private Project testProject;

    @BeforeEach
    void setUp(@TempDir Path tempDir) {
        testProject = ProjectBuilder.builder()
                .withProjectDir(tempDir.toFile())
                .build();
    }

    @Test
    void extensionCreated() {
        testProject.getPluginManager().apply(DockerBaseImageBuildPlugin.class);
        final BaseImageExtension extension = testProject.getExtensions().getByType(BaseImageExtension.class);
    }
}