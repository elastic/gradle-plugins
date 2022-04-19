package co.elastic.gradle;

import org.gradle.testkit.runner.GradleRunner;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

public class TestkitIntegrationTest {

    protected GradleTestkitHelper helper;
    protected GradleRunner gradleRunner;

    @BeforeEach
    void setUp(@TempDir Path testProjectDir) throws IOException {
        helper = getHelper(testProjectDir);
        gradleRunner = getGradleRunner(testProjectDir);
    }

    protected GradleRunner getGradleRunner(Path testProjectDir) {
        final GradleRunner gradleRunner = GradleRunner.create()
                .withProjectDir(testProjectDir.toFile())
                .withPluginClasspath();
        return gradleRunner;
    }

    protected GradleTestkitHelper getHelper(Path testProjectDir) throws IOException {
        GradleTestkitHelper helper = new GradleTestkitHelper(testProjectDir);
        Files.createDirectories(helper.projectDir());
        return helper;
    }

}
