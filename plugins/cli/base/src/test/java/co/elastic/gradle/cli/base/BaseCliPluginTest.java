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
package co.elastic.gradle.cli.base;

import co.elastic.gradle.lifecycle.LifecyclePlugin;
import org.gradle.api.Project;
import org.gradle.testfixtures.ProjectBuilder;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;

class BaseCliPluginTest {

    private Project testProject;

    @BeforeEach
    void setUp(@TempDir Path tempDir) {
        testProject = ProjectBuilder.builder()
                .withProjectDir(tempDir.toFile())
                .build();
    }

    @Test
    void apply() {
        testProject.getPluginManager().apply(BaseCliPlugin.class);
        testProject.getTasks().named(BaseCliPlugin.SYNC_TASK_NAME);
        testProject.getPluginManager().apply(LifecyclePlugin.class);
        Assertions.assertTrue(
            testProject.getTasks().getByName("syncBinDir").getDependsOn().contains(
                    testProject.getTasks().named(BaseCliPlugin.SYNC_TASK_NAME)
            )
        );
    }
}