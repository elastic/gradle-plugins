package co.elastic.gradle;

import org.gradle.testkit.runner.GradleRunner;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;

public class TestkitIntegrationTest {

    protected GradleTestkitHelper helper;
    protected GradleRunner gradleRunner;

    @BeforeEach
    void setUp(@TempDir Path testProjectDir) {
        helper = new GradleTestkitHelper(testProjectDir);
        gradleRunner = GradleRunner.create()
                .withProjectDir(testProjectDir.toFile())
                .withPluginClasspath();
    }

}
