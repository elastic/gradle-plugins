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
package co.elastic.gradle.dockerbase.lockfile;

import co.elastic.gradle.utils.Architecture;
import co.elastic.gradle.utils.docker.UnchangingContainerReference;
import org.jetbrains.annotations.NotNull;
import org.junit.jupiter.api.Test;

import java.io.*;
import java.util.List;
import java.util.Map;
import java.util.Objects;

import static org.junit.jupiter.api.Assertions.assertEquals;

public class BaseLockfileTest {
    @Test
    public void shouldSerdeYaml() throws IOException {
        BaseLockfile lockfile = getSampleLockfile();
        assertSampleLockfile(lockfile);
        try (ByteArrayOutputStream outStream = new ByteArrayOutputStream()) {
            BaseLockfile.write(lockfile, new OutputStreamWriter(outStream));
            try (ByteArrayInputStream inStream = new ByteArrayInputStream(outStream.toByteArray())) {
                assertSampleLockfile(BaseLockfile.parse(new InputStreamReader(inStream)));
            }
        }
    }

    @Test
    public void keepFileFormatCompatability() throws IOException {
        final BaseLockfile lockfile = BaseLockfile.parse(new InputStreamReader(
                Objects.requireNonNull(getClass().getResourceAsStream("/lockfile.yaml"))
        ));
        assertSampleLockfile(lockfile);
        assertEquals(getSampleLockfile(), lockfile);
    }

    @NotNull
    private BaseLockfile getSampleLockfile() {
        return new BaseLockfile(
                Map.of(
                        Architecture.X86_64,
                        new Packages(
                                List.of(
                                        new UnchangingPackage(
                                                "jq", "1.5", "12.el8", "x86_64"
                                        )
                                )
                        ),

                        Architecture.AARCH64,
                        new Packages(
                                List.of(
                                        new UnchangingPackage(
                                                "jq", "1.5", "12.el8", "aarch64"
                                        )
                                )
                        )
                ),
                Map.of(
                        Architecture.X86_64,
                        new UnchangingContainerReference(
                                "repo_x86", "tag_x86", "digest_x86"
                        ),

                        Architecture.AARCH64,
                        new UnchangingContainerReference("repo_arm", "tag_arm", "digest_arm")

                )
        );
    }

    private void assertSampleLockfile(BaseLockfile lockfile) {
        Packages x86 = lockfile.getPackages().get(Architecture.X86_64);
        Packages arm = lockfile.getPackages().get(Architecture.AARCH64);

        final UnchangingContainerReference armImage = lockfile.getImage().get(Architecture.AARCH64);
        final UnchangingContainerReference x86Image = lockfile.getImage().get(Architecture.X86_64);

        assertEquals(x86Image.getRepository(), "repo_x86");
        assertEquals(x86Image.getTag(), "tag_x86");
        assertEquals(x86Image.getDigest(), "digest_x86");
        assertEquals(x86.getPackages().size(), 1);
        assertEquals(x86.getPackages().get(0).getName(), "jq");
        assertEquals(x86.getPackages().get(0).getVersion(), "1.5");
        assertEquals(x86.getPackages().get(0).getRelease(), "12.el8");
        assertEquals(x86.getPackages().get(0).getArchitecture(), "x86_64");

        assertEquals(armImage.getRepository(), "repo_arm");
        assertEquals(armImage.getTag(), "tag_arm");
        assertEquals(armImage.getDigest(), "digest_arm");
        assertEquals(arm.getPackages().size(), 1);
        assertEquals(arm.getPackages().get(0).getName(), "jq");
        assertEquals(arm.getPackages().get(0).getVersion(), "1.5");
        assertEquals(arm.getPackages().get(0).getRelease(), "12.el8");
        assertEquals(arm.getPackages().get(0).getArchitecture(), "aarch64");
    }
}
