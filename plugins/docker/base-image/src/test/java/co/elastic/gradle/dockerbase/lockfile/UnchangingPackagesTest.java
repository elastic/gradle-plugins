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

import co.elastic.gradle.dockerbase.OSDistribution;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

public class UnchangingPackagesTest {
    @Test
    public void shouldConvertToAptPackage() {
        final UnchangingPackage pkg = new UnchangingPackage("jq", "1.5", "rel", "amd64");
        assertEquals("jq", pkg.getName());
        assertEquals("1.5", pkg.getVersion());
        assertEquals("rel", pkg.getRelease());
        assertEquals("amd64", pkg.getArchitecture());
        assertEquals("jq=1.5-rel", pkg.getPackageName(OSDistribution.DEBIAN));
    }

    @Test
    public void shouldConvertToAptPackageNoRel() {
        final UnchangingPackage pkg = new UnchangingPackage("jq", "1.5", "", "amd64");
        assertEquals("jq", pkg.getName());
        assertEquals("1.5", pkg.getVersion());
        assertEquals("", pkg.getRelease());
        assertEquals("amd64", pkg.getArchitecture());
        assertEquals("jq=1.5", pkg.getPackageName(OSDistribution.DEBIAN));
    }

    @Test
    public void shouldConvertToYumPackage() {
        assertEquals(
                "jq-1.5-12.el8.x86_64",
                new UnchangingPackage("jq", "1.5", "12.el8", "x86_64")
                        .getPackageName(OSDistribution.CENTOS)
        );
    }
}
