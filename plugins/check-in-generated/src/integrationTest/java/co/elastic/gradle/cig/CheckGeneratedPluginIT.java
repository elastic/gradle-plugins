/*
 * Copyright Elasticsearch B.V. and/or licensed to Elasticsearch B.V. under one
 * or more contributor license agreements. See the NOTICE file distributed with
 * this work for additional information regarding copyright
 * ownership. Elasticsearch B.V. licenses this file to you under
 * the Apache License, Version 2.0 (the "License"); you may
 * not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *	http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied.  See the License for the
 * specific language governing permissions and limitations
 * under the License.
 */
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
