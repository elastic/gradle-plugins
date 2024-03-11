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
      final BuildResult result = gradleRunner.withArguments("--warning-mode", "fail", "-s", "check")
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
        final BuildResult result = gradleRunner.withArguments("--warning-mode", "fail", "-s", "check")
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
        final BuildResult result = gradleRunner.withArguments("--warning-mode", "fail", "-s", "check")
                .build();

        Assert.assertEquals(
                Objects.requireNonNull(result.task(":foo")).getOutcome(),
                TaskOutcome.UP_TO_DATE
        );
    }


}