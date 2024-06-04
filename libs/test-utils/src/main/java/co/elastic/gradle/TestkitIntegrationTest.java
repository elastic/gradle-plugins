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
package co.elastic.gradle;

import org.gradle.internal.impldep.org.apache.commons.io.FileUtils;
import org.gradle.testkit.runner.GradleRunner;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

public class TestkitIntegrationTest {

    protected GradleTestkitHelper helper;
    protected GradleRunner gradleRunner;
    protected Path gradleHome;

    @BeforeEach
    void setUp(@TempDir Path testProjectDir) throws IOException {
        helper = getHelper(testProjectDir);
        gradleRunner = getGradleRunner(testProjectDir);
        gradleHome = Files.createTempDirectory("gradle-home");
    }

    @AfterEach
    void tearDown() throws IOException {
        try {
            FileUtils.deleteDirectory(gradleHome.toFile());
        } catch (IOException e) {
            // Best effort because
            System.err.println("Failed to remove temporary GRADLE_HOME " + e.getMessage());
        }
    }

    protected GradleRunner getGradleRunner(Path testProjectDir) throws IOException {
        final Path testKitDir = testProjectDir.resolve("test-kit-dir");
        Files.createDirectories(testKitDir);
        final GradleRunner gradleRunner = GradleRunner.create()
                .withProjectDir(testProjectDir.toFile())
                .withTestKitDir(gradleHome.toFile())
                .withPluginClasspath();
        return gradleRunner;
    }

    protected GradleTestkitHelper getHelper(Path testProjectDir) throws IOException {
        GradleTestkitHelper helper = new GradleTestkitHelper(testProjectDir);
        Files.createDirectories(helper.projectDir());
        return helper;
    }

}
