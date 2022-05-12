package co.elastic.gradle.lifecycle;

import org.gradle.api.Project;
import org.gradle.testfixtures.ProjectBuilder;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

class MultiArchLifecyclePluginTest {
    private Project testProject;

    @BeforeEach
    void setUp(@TempDir Path tempDir) {
        testProject = ProjectBuilder.builder()
                .withProjectDir(tempDir.toFile())
                .build();
    }

    @Test
    void tasksCreated() {
        testProject.getPluginManager().apply(MultiArchLifecyclePlugin.class);
        for (String type : Set.of("ForPlatform", "PlatformIndependent", "CombinePlatform", "")) {
            assertTaskCreated(LifecyclePlugin.PUBLISH_TASK_NAME + type);
            assertTaskCreated("check" + type);
            assertTaskCreated("assemble" + type);
            assertTaskCreated("build" + type);
        }
        assertTaskCreated(LifecyclePlugin.PUBLISH_TASK_NAME);
        assertTaskCreated(LifecyclePlugin.PRE_COMMIT_TASK_NAME);
        assertTaskCreated(LifecyclePlugin.RESOLVE_ALL_DEPENDENCIES_TASK_NAME);
        assertTaskCreated(LifecyclePlugin.SYNC_BIN_DIR_TASK_NAME);
    }

    void assertTaskCreated(String taskName) {
        assertNotNull(testProject.getTasks().findByName(taskName),
                "Expected task " + taskName + " to exist but it did not." +
                " The list of tasks is:\n" + testProject.getTasks().stream().toList()
        );
        assertNotNull(testProject.getTasks().getByName(taskName).getDescription());
        assertNotNull(testProject.getTasks().getByName(taskName).getGroup());
    }
}