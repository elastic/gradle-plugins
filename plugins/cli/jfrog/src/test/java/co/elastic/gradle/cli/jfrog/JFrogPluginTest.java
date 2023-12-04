package co.elastic.gradle.cli.jfrog;

import co.elastic.gradle.cli.base.CliExtension;
import org.gradle.api.Project;
import org.gradle.testfixtures.ProjectBuilder;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertNotNull;

class JFrogPluginTest {

    private Project testProject;

    @BeforeEach
    void setUp(@TempDir Path tempDir) {
        testProject = ProjectBuilder.builder()
                .withProjectDir(tempDir.toFile())
                .build();
    }

    @Test
    public void testApply() {
        testProject.getPluginManager().apply(JFrogPlugin.class);
        final CliExtension cli = (CliExtension) testProject.getExtensions().findByName("cli");
        assertNotNull(cli);
        assertNotNull(cli.getExtensions().findByName("jfrog"));
    }

}