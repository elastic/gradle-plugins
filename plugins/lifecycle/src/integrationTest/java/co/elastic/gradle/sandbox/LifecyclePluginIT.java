package co.elastic.gradle.sandbox;

import co.elastic.gradle.TestkitIntegrationTest;
import org.gradle.testkit.runner.BuildResult;
import org.gradle.testkit.runner.TaskOutcome;
import org.junit.jupiter.api.Test;

import java.util.Objects;

import static org.junit.jupiter.api.Assertions.*;

class LifecyclePluginIT extends TestkitIntegrationTest {

    @Test
    void apply() {
        helper.buildScript("""
              plugins {
                 id("co.elastic.lifecycle")
              }
              """);
        final BuildResult result = gradleRunner.withArguments("-s", "publish")
                .build();
    }

    @Test
    void resolveAllDependencies() {
        helper.buildScript("""
                plugins {
                   id("co.elastic.lifecycle")
                   id("java")
                }
                repositories {
                   mavenCentral()
                }
                dependencies {
                   implementation("commons-io:commons-io:2.11.0")
                   testImplementation("org.junit.jupiter:junit-jupiter:5.7.2")
                }                             
                """);
        final BuildResult result = gradleRunner
                .withArguments("-s", "resolveAllDependencies")
                .build();

        assertEquals(
                TaskOutcome.SUCCESS,
                Objects.requireNonNull(result.task(":resolveAllDependencies")).getOutcome()
        );

        final BuildResult resultOffline = gradleRunner
                .withArguments("-offline", "resolveAllDependencies")
                .build();

        assertEquals(
                TaskOutcome.UP_TO_DATE,
                Objects.requireNonNull(resultOffline.task(":resolveAllDependencies")).getOutcome()
        );

    }
}