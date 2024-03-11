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

import org.gradle.api.DefaultTask;
import org.gradle.api.file.FileCollection;
import org.gradle.api.provider.MapProperty;
import org.gradle.api.tasks.InputFiles;
import org.gradle.api.tasks.Internal;
import org.gradle.api.tasks.PathSensitive;
import org.gradle.api.tasks.PathSensitivity;

import java.io.File;
import java.util.Collection;
import java.util.Set;
import java.util.function.Predicate;
import java.util.function.Supplier;
import java.util.stream.Collectors;

public abstract class AbstractFileMapTask extends DefaultTask {
    @Internal
    public abstract MapProperty<File, File> getMap();

    @InputFiles
    @PathSensitive(PathSensitivity.RELATIVE)
    public Set<File> getFromFiles() {
        return getFrom(getMap().get()::keySet, File::isFile);
    }

    @InputFiles // That's right, no InputDirectories in Gradle
    @PathSensitive(PathSensitivity.RELATIVE)
    public FileCollection getFromDirs() {
        return getFrom(getMap().get()::keySet, File::isDirectory).stream()
                .map( each -> (FileCollection) getProject().fileTree(each))
                .reduce(                        (tree1, tree2) -> {
                    final FileCollection files = getProject().files();
                    files.plus(tree1);
                    files.plus(tree2);
                    return files;
                })
                .orElse(getProject().files());
    }


    protected Set<File> getFrom(Supplier<Collection<File>> data, Predicate<File> filter) {
        return data.get()
                .stream()
                .filter(filter)
                .collect(Collectors.toSet());
    }
}
