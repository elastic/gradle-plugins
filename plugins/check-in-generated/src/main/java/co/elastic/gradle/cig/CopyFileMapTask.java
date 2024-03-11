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
package co.elastic.gradle.cig;

import org.apache.commons.io.FileUtils;
import org.gradle.api.GradleException;
import org.gradle.api.tasks.*;

import java.io.File;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.util.Map;
import java.util.Set;

abstract public class CopyFileMapTask extends AbstractFileMapTask {

    @OutputFiles
    public Set<File> getToFiles() {
        return getFrom(getMap().get()::values, File::isFile);
    }

    @OutputDirectories
    public Set<File> getToDirs() {
        return getFrom(getMap().get()::values, File::isDirectory);
    }

    @TaskAction
    public void copyFiles() {
        final Map<File, File> map = getMap().get();
        if (map.isEmpty()) {
            throw new GradleException("The map of files can't be empty");
        }
        map.forEach((from, to) -> {
            if (!from.exists()) {
                throw new GradleException("Expected " + from + " to exist but it did not");
            }
            try {
                if (from.isDirectory()) {
                    FileUtils.copyDirectory(from, to);
                } else {
                    FileUtils.copyFile(from, to);
                }
            } catch (IOException e) {
                throw new UncheckedIOException(e);
            }
        });
    }

}
