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

import org.gradle.api.file.FileTreeElement;
import org.gradle.api.file.ProjectLayout;
import org.gradle.api.file.RegularFileProperty;
import org.gradle.api.provider.ListProperty;
import org.gradle.api.specs.Spec;
import org.gradle.api.tasks.*;
import org.gradle.api.tasks.util.PatternFilterable;
import org.gradle.api.tasks.util.PatternSet;

import javax.inject.Inject;
import java.io.File;

public abstract class LicenseHeaderConfig {

    private final PatternSet patternSet = new PatternSet();

    public LicenseHeaderConfig() {
        getHeaderFile().convention(getProjectLayout().getProjectDirectory().file("src/header.txt"));
    }

    @InputFile
    @PathSensitive(PathSensitivity.NONE)
    public abstract RegularFileProperty getHeaderFile();

    @InputFiles
    @PathSensitive(PathSensitivity.RELATIVE)
    @SkipWhenEmpty
    public abstract ListProperty<File> getFiles();

    @Inject
    protected abstract ProjectLayout getProjectLayout();

    @Internal
    protected PatternFilterable getFilter() {
        return patternSet;
    }

    public PatternSet include(String... includes) {
        return patternSet.include(includes);
    }

    public PatternSet include(Iterable includes) {
        return patternSet.include(includes);
    }

    public PatternSet include(Spec<FileTreeElement> spec) {
        return patternSet.include(spec);
    }

    public PatternSet exclude(String... excludes) {
        return patternSet.exclude(excludes);
    }

    public PatternSet exclude(Iterable excludes) {
        return patternSet.exclude(excludes);
    }

    public PatternSet exclude(Spec<FileTreeElement> spec) {
        return patternSet.exclude(spec);
    }
}
