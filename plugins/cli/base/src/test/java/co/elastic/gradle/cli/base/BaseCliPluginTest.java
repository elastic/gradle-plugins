package co.elastic.gradle.cli.base;

import co.elastic.gradle.lifecycle.LifecyclePlugin;
import org.gradle.api.Project;
import org.gradle.testfixtures.ProjectBuilder;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;

class BaseCliPluginTest {

    private Project testProject;

    @BeforeEach
    void setUp(@TempDir Path tempDir) {
        testProject = ProjectBuilder.builder()
                .withProjectDir(tempDir.toFile())
                .build();
    }

    @Test
    void apply() {
        testProject.getPluginManager().apply(BaseCliPlugin.class);
        testProject.getTasks().named(BaseCliPlugin.SYNC_TASK_NAME);
        testProject.getPluginManager().apply(LifecyclePlugin.class);
        Assertions.assertTrue(
            testProject.getTasks().getByName("syncBinDir").getDependsOn().contains(
                    testProject.getTasks().named(BaseCliPlugin.SYNC_TASK_NAME)
            )
        );
    }
}