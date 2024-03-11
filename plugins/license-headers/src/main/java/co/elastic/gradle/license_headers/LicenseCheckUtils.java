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
package co.elastic.gradle.license_headers;

import org.gradle.api.GradleException;

import java.io.File;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class LicenseCheckUtils {

    public static Map<Path, ViolationReason> nonCompliantFilesWithReason(Path projectDir, String[] expectedHeader, List<File> files) {
        Map<Path, ViolationReason> brokenFiles = new HashMap<>();

        for (File file : files) {
            final String[] fileHeader;
            try {
                fileHeader = Files.lines(file.toPath()).limit(expectedHeader.length).toArray(String[]::new);
            } catch (IOException| UncheckedIOException e) {
                throw new GradleException("Failed to read " + projectDir.relativize(file.toPath()), e);
            }
            if (expectedHeader.length > fileHeader.length) {
                brokenFiles.put(
                        file.toPath(),
                        new ViolationReason("File has fewer lines than the header", ViolationReason.Type.SHORT_FILE)
                );
            } else for (int i = 0; i < expectedHeader.length; i++) {
                if (!fileHeader[i].equals(expectedHeader[i])) {
                    if (i == 0) {
                        brokenFiles.put(
                                file.toPath(),
                                new ViolationReason("Missing header", ViolationReason.Type.MISSING_HEADER)
                        );
                    } else {
                        brokenFiles.put(
                                file.toPath(),
                                new ViolationReason("Header mismatch at line " + (i + 1), ViolationReason.Type.LINE_MISS_MATCH)
                        );
                    }
                    break;
                }
            }
        }

        return brokenFiles;
    }

}
