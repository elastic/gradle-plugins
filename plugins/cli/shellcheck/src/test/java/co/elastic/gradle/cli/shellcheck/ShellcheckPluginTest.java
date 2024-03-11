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
package co.elastic.gradle.cli.shellcheck;

import co.elastic.gradle.cli.base.CliExtension;
import co.elastic.gradle.lifecycle.LifecyclePlugin;
import co.elastic.gradle.lifecycle.MultiArchLifecyclePlugin;
import org.gradle.api.Project;
import org.gradle.api.Task;
import org.gradle.api.tasks.TaskProvider;
import org.gradle.testfixtures.ProjectBuilder;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ShellcheckPluginTest {

    private Project testProject;

    @BeforeEach
    void setUp(@TempDir Path tempDir) {
        testProject = ProjectBuilder.builder()
                .withProjectDir(tempDir.toFile())
                .build();
    }

    @Test
    public void testApply() {
        testProject.getPluginManager().apply(ShellcheckPlugin.class);
        final CliExtension cli = (CliExtension) testProject.getExtensions().findByName("cli");
        assertNotNull(cli);
        assertNotNull(cli.getExtensions().findByName("shellcheck"));
        assertNotNull(testProject.getTasks().findByName("shellcheck"));
    }

    @Test
    public void testApplyWithBasePlugin() {
        testProject.getPluginManager().apply(ShellcheckPlugin.class);
        testProject.getPluginManager().apply(MultiArchLifecyclePlugin.class);
        testProject.getPluginManager().apply(LifecyclePlugin.class);
        final TaskProvider<Task> shellcheck = testProject.getTasks().named("shellcheck");
        assertTrue(testProject.getTasks().getByName("checkPlatformIndependent").getDependsOn().contains(shellcheck));
        assertTrue(testProject.getTasks().getByName("check").getDependsOn().contains(shellcheck));
    }

}