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
package co.elastic.gralde.wrapper;

import co.elastic.gradle.AssertContains;
import co.elastic.gradle.TestkitIntegrationTest;
import co.elastic.gradle.utils.Architecture;
import co.elastic.gradle.utils.OS;
import org.apache.commons.io.IOUtils;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;

class WrapperPluginIT extends TestkitIntegrationTest {

    @Test
    void apply() throws IOException, InterruptedException {
        helper.buildScript(String.format("""
                            import %s.*
                            import %s.*
                            plugins {
                                id("co.elastic.wrapper-provision-jdk")
                            }
                            tasks.wrapperProvisionJdk {
                                jdkCacheDir.set("\\${JENKINS_HOME:-\\$HOME}/.gradle/jdks")
                                javaReleaseName.set("17.0.10_7")
                                checksums.set(
                                    mapOf(
                                        LINUX to mapOf(
                                            X86_64 to "a8fd07e1e97352e97e330beb20f1c6b351ba064ca7878e974c7d68b8a5c1b378",
                                            AARCH64 to "6e4201abfb3b020c1fb899b7ac063083c271250bf081f3aa7e63d91291a90b74"
                                        ),
                                        DARWIN to mapOf(
                                            X86_64 to "e16ee89d3304bb2ba706f9a7b0ba279725c2aea55d5468336f8de4bb859f300d",
                                            AARCH64 to "a6ec3b94f61695e8f445ee508411c56a2ce0cabc16ea4c4296ff062d13559d92"
                                        )
                                    )
                                )
                            }
                        """,
                OS.class.getName(), Architecture.class.getName()
        ));

        gradleRunner.withArguments("--warning-mode", "fail", "-s", "wrapper")
                .build();

        final String gradlewContent = Files.readString(helper.projectDir().resolve("gradlew"));
        AssertContains.assertContains(
                gradlewContent,
                "https://github.com/adoptium/temurin17-binaries/releases/download/jdk-17.0.10"
        );
        AssertContains.assertDoesNotContain(
                gradlewContent,
                "%{"
        );

        // Remove the build-script so it won't fail on finding the plugin as it's running without testkit this time
        helper.buildScript("");

        final Process process = new ProcessBuilder()
                .directory(helper.projectDir().toFile())
                .command(helper.projectDir().resolve("gradlew").toAbsolutePath().toString(), "help", "--no-scan")
                .redirectOutput(ProcessBuilder.Redirect.PIPE)
                .redirectError(ProcessBuilder.Redirect.PIPE)
                .start();

        do {
            IOUtils.copy(process.getInputStream(), System.out);
            IOUtils.copy(process.getErrorStream(), System.err);
        } while (process.isAlive());


        final int exitCode = process.waitFor();
        if (exitCode != 0) {
            Assertions.fail("Failed to run generated wrapper " + exitCode + "\n" + gradlewContent);
        }

    }
}