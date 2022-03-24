package co.elastic.gradle.cli.shellcheck;

import co.elastic.gradle.cli.base.CliExtension;
import co.elastic.gradle.lifecycle.LifecyclePlugin;
import co.elastic.gradle.lifecycle.MultiArchLifecyclePlugin;
import org.gradle.api.Project;
import org.gradle.api.Task;
import org.gradle.api.tasks.TaskProvider;
import org.gradle.testfixtures.ProjectBuilder;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ShellcheckPluginTest {

    private Project testProject;

    @BeforeEach
    void setUp(@TempDir Path tempDir) {
        testProject = ProjectBuilder.builder()
                .withProjectDir(tempDir.toFile())
                .build();
    }

    @Test
    public void testApply() {
        testProject.getPluginManager().apply(ShellcheckPlugin.class);
        final CliExtension cli = (CliExtension) testProject.getExtensions().findByName("cli");
        assertNotNull(cli);
        assertNotNull(cli.getExtensions().findByName("shellcheck"));
        assertNotNull(testProject.getTasks().findByName("shellcheck"));
    }

    @Test
    public void testApplyWithBasePlugin() {
        testProject.getPluginManager().apply(ShellcheckPlugin.class);
        testProject.getPluginManager().apply(MultiArchLifecyclePlugin.class);
        testProject.getPluginManager().apply(LifecyclePlugin.class);
        final TaskProvider<Task> shellcheck = testProject.getTasks().named("shellcheck");
        assertTrue(testProject.getTasks().getByName("checkPlatformIndependent").getDependsOn().contains(shellcheck));
        assertTrue(testProject.getTasks().getByName("check").getDependsOn().contains(shellcheck));
    }

}