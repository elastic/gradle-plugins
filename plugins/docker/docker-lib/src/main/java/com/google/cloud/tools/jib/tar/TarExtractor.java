/*
 * Copyright 2019 Google LLC.
 *
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not
 * use this file except in compliance with the License. You may obtain a copy of
 * the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS, WITHOUT
 * WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the
 * License for the specific language governing permissions and limitations under
 * the License.
 */

package com.google.cloud.tools.jib.tar;

import co.elastic.gradle.docker.base.ExtractCompressedTar;

import java.io.IOException;
import java.nio.file.Path;

// HACK: Jib doesn't support compressed tar archives, see https://github.com/GoogleContainerTools/jib/issues/2895
//       Thus we replace the class that deals with extracting tar archives to add support for it.
//       Ideally we'll contribute it upstream and will be able to remove the hack.

/** Extracts a tarball. */
public class TarExtractor {

    /**
     * Extracts a tarball to the specified destination.
     *
     * @param source the tarball to extract
     * @param destination the output directory
     * @throws IOException if extraction fails
     */
    public static void extract(Path source, Path destination) throws IOException {
        ExtractCompressedTar.extract(source, destination);
    }
}
