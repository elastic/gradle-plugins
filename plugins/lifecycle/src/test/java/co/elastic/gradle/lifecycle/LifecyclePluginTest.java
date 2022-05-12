package co.elastic.gradle.lifecycle;

import org.gradle.api.Project;
import org.gradle.api.internal.plugins.PluginApplicationException;
import org.gradle.api.publish.maven.plugins.MavenPublishPlugin;
import org.gradle.testfixtures.ProjectBuilder;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

class LifecyclePluginTest {

    private Project testProject;

    @BeforeEach
    void setUp(@TempDir Path tempDir) {
        testProject = ProjectBuilder.builder()
                .withProjectDir(tempDir.toFile())
                .build();
    }

    @Test
    void tasksCreated() {
        testProject.getPluginManager().apply(LifecyclePlugin.class);
        assertTaskCreated(LifecyclePlugin.PUBLISH_TASK_NAME);
        assertTaskCreated(LifecyclePlugin.PRE_COMMIT_TASK_NAME);
        assertTaskCreated(LifecyclePlugin.RESOLVE_ALL_DEPENDENCIES_TASK_NAME);
        assertTaskCreated(LifecyclePlugin.SYNC_BIN_DIR_TASK_NAME);
    }

    @Test
    void worksWithPublishing() {
        testProject.getPluginManager().apply(MavenPublishPlugin.class);
        testProject.getPluginManager().apply(LifecyclePlugin.class);
        assertTaskCreated(LifecyclePlugin.PUBLISH_TASK_NAME);
    }

    @Test
    void worksWithPublishingWrongOrder() {
        testProject.getPluginManager().apply(LifecyclePlugin.class);
        assertThrows(PluginApplicationException.class,
                () -> testProject.getPluginManager().apply(MavenPublishPlugin.class)
        );
    }

    void assertTaskCreated(String taskName) {
        assertNotNull(testProject.getTasks().findByName(taskName), "Expected task " + taskName + " to exist");
        assertNotNull(testProject.getTasks().getByName(taskName).getDescription());
        assertNotNull(testProject.getTasks().getByName(taskName).getGroup());
    }
}