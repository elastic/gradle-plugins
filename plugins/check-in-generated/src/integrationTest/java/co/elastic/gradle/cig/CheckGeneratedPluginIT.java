package co.elastic.gradle.cig;

import co.elastic.gradle.AssertContains;
import co.elastic.gradle.TestkitIntegrationTest;
import org.gradle.api.internal.tasks.TaskExecutionOutcome;
import org.gradle.testkit.runner.BuildResult;
import  org.gradle.testkit.runner.TaskOutcome;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

public class CheckGeneratedPluginIT extends TestkitIntegrationTest {

    @Test
    public void testPlugin() {
        helper.buildScript("""
                plugins {
                   id("co.elastic.check-in-generated")
                }
                val doGenerate by tasks.registering {
                   doLast {
                      file("build/bar/bar").mkdirs()
                      file("build/foo.txt").writeText("This is FOO")
                      file("build/bar/bar/bar.txt").writeText("This is nested BAR")
                   }
                }
                checkInGenerated {
                    generatorTask.set(doGenerate)
                    map.set(mapOf(
                           project.file("build/foo.txt") to project.file("foo.txt"),
                           project.file("build/bar") to project.file("bar")
                    ))
                }
                """);

        // Check that verification fails on missing
        assertVerificationFails(true);

        // Generate and check that it passes
        gradleRunner.withArguments("--warning-mode", "fail", "-s", "generate")
                .build();
        gradleRunner.withArguments("--warning-mode", "fail", "-s", "verifyGenerated")
                .build();

        // Check should be skipped on second call
        BuildResult result = gradleRunner.withArguments("--warning-mode", "fail", "-s", "verifyGenerated")
                .build();
        Assertions.assertEquals(TaskOutcome.UP_TO_DATE, result.task(":verifyGenerated").getOutcome());

        // Change what was checked in and verify that it's detected
        helper.writeFile("foo.txt", "This is NO LONGER FOO");
        helper.writeFile("bar/bar/bar.txt", "This is NO LONGER BAR");

        assertVerificationFails();

        // RE-generate and this time change what was generated instead of what is checked in
        gradleRunner.withArguments("--warning-mode", "fail", "-s", "generate")
                .build();
        gradleRunner.withArguments("--warning-mode", "fail", "-s", "verifyGenerated")
                .build();

        // Change what was checked in and verify that it's detected
        helper.writeFile("build/foo.txt", "This is NO LONGER FOO");
        helper.writeFile("build/bar/bar/bar.txt", "This is NO LONGER BAR");

        assertVerificationFails();
    }

    private void assertVerificationFails() {
        assertVerificationFails(false);
    }

    private void assertVerificationFails(boolean initial) {
        // Call the generate target here to proove that it doesn't void the verification if done in a single call
        BuildResult result = gradleRunner.withArguments("--warning-mode", "fail", "-s", "generate", "verifyGenerated")
                .buildAndFail();
        AssertContains.assertContains(result.getOutput(), "The following files did not match what was generated:");
        AssertContains.assertContains(result.getOutput(), " - foo.txt");
        if (initial) {
            AssertContains.assertContains(result.getOutput(), " - bar");
        } else {
            AssertContains.assertContains(result.getOutput(), " - bar/bar/bar.txt");
        }
    }

}
