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
        final BuildResult result = gradleRunner.withArguments("--warning-mode", "fail", "-s", "publish")
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
                .withArguments("--warning-mode", "fail", "-s", "resolveAllDependencies")
                .build();

        assertEquals(
                TaskOutcome.SUCCESS,
                Objects.requireNonNull(result.task(":resolveAllDependencies")).getOutcome()
        );

        final BuildResult resultOffline = gradleRunner
                .withArguments("--warning-mode", "fail", "-offline", "resolveAllDependencies")
                .build();

        assertEquals(
                TaskOutcome.UP_TO_DATE,
                Objects.requireNonNull(resultOffline.task(":resolveAllDependencies")).getOutcome()
        );

    }
}