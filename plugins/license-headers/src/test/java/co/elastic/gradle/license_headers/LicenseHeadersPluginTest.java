package co.elastic.gradle.license_headers;

import org.gradle.api.Project;
import org.gradle.testfixtures.ProjectBuilder;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;


class LicenseHeadersPluginTest {

    private Project testProject;

    @BeforeEach
    void setUp(@TempDir Path tempDir) {
        testProject = ProjectBuilder.builder()
                .withProjectDir(tempDir.toFile())
                .build();
    }

    @Test
    void applyCustomRule() {
        testProject.getPluginManager().apply(LicenseHeadersPlugin.class);
        final LicenseHeadersExtension extension = testProject.getExtensions().getByType(LicenseHeadersExtension.class);
        extension.check(testProject.fileTree("src"));
        extension.check(testProject.fileTree("src"), config -> {
            config.include("foo");
        });
        assertEquals(2, extension.getConfigs().get().size());
    }
}