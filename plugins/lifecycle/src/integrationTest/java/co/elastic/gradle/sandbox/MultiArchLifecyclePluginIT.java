package co.elastic.gradle.sandbox;

import co.elastic.gradle.TestkitIntegrationTest;
import org.gradle.internal.impldep.org.junit.Assert;
import org.gradle.testkit.runner.BuildResult;
import org.gradle.testkit.runner.TaskOutcome;
import org.junit.jupiter.api.Test;

import java.util.Objects;

class MultiArchLifecyclePluginIT extends TestkitIntegrationTest {

  @Test
  public void testCheckConsistency() {
      helper.buildScript("""
              plugins {
                 id("co.elastic.lifecycle-multi-arch")
              }
              """);
      final BuildResult result = gradleRunner.withArguments("-s", "check")
              .build();

      Assert.assertEquals(
              Objects.requireNonNull(result.task(":checkConsistency")).getOutcome(),
              TaskOutcome.SUCCESS
      );
  }

    @Test
    public void testCheckConsistencyDetection() {
        helper.buildScript("""
              plugins {
                 id("co.elastic.lifecycle-multi-arch")
              }
              tasks.register("foo")
              tasks.named("check") {
                 dependsOn("foo")
              }
              """);
        final BuildResult result = gradleRunner.withArguments("-s", "check")
                .buildAndFail();

        Assert.assertEquals(
                Objects.requireNonNull(result.task(":checkConsistency")).getOutcome(),
                TaskOutcome.FAILED
        );
    }

    @Test
    public void dependenciesAdded() {
        helper.buildScript("""
              plugins {
                 id("co.elastic.lifecycle-multi-arch")
              }
              tasks.register("foo")
              tasks.named("checkForPlatform") {
                 dependsOn("foo")
              }
              """);
        final BuildResult result = gradleRunner.withArguments("-s", "check")
                .build();

        Assert.assertEquals(
                Objects.requireNonNull(result.task(":foo")).getOutcome(),
                TaskOutcome.UP_TO_DATE
        );
    }


}