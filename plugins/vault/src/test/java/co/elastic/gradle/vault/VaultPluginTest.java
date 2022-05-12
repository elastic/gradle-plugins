package co.elastic.gradle.vault;

import org.gradle.api.Project;
import org.gradle.testfixtures.ProjectBuilder;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;

class VaultPluginTest {

    private Project testProject;

    @BeforeEach
    void setUp(@TempDir Path tempDir) {
        testProject = ProjectBuilder.builder()
                .withProjectDir(tempDir.toFile())
                .build();
    }

    @SuppressWarnings("unchecked")
    @Test
    void pluginCanBeAppliedOnSettings() {
        testProject.getGradle().settingsEvaluated( settings -> settings.getPluginManager().apply(VaultSettingsPlugin.class));
    }

    @Test
    void pluginCanBeAppliedOnProject() {
        testProject.getPluginManager().apply(VaultPlugin.class);
    }
}