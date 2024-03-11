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
package co.elastic.gradle.buildscan.xunit;

import org.gradle.api.file.FileCollection;
import org.gradle.api.internal.tasks.testing.TestExecuter;
import org.gradle.api.internal.tasks.testing.TestExecutionSpec;
import org.gradle.api.provider.ListProperty;
import org.gradle.api.provider.ProviderFactory;
import org.gradle.api.tasks.InputFiles;
import org.gradle.api.tasks.SkipWhenEmpty;
import org.gradle.api.tasks.testing.AbstractTestTask;
import org.gradle.internal.logging.progress.ProgressLoggerFactory;

import javax.inject.Inject;
import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.util.HashSet;
import java.util.List;


public abstract class XUnitBuildScanImporterTask extends AbstractTestTask {

    private final ProgressLoggerFactory getProgressLoggerFactory;

    @InputFiles
    @SkipWhenEmpty
    public abstract ListProperty<File> getFrom();

    @Inject
    public XUnitBuildScanImporterTask(ProgressLoggerFactory getProgressLoggerFactory) throws IOException {
        super();
        this.getProgressLoggerFactory = getProgressLoggerFactory;
        File binaryResultsDir = new File(
                getProject().getBuildDir(),
                "externalTestImport"
        );
        Files.createDirectories(
        binaryResultsDir.toPath()
        );
        getBinaryResultsDirectory().set(binaryResultsDir);
        getReports().getJunitXml().getRequired().set(false);
        getReports().getHtml().getRequired().set(false);
        setIgnoreFailures(true);
    }

    public void from(File file) {
        getFrom().set(List.of(file));
    }

    public void from(FileCollection file) {
        getFrom().set(getProviderFactory().provider(
                () -> file.getFiles()
        ));
    }

    @Inject
    protected abstract ProviderFactory getProviderFactory();


    @Override
    protected ProgressLoggerFactory getProgressLoggerFactory() {
        return getProgressLoggerFactory;
    }

    @Override
    protected TestExecuter<? extends TestExecutionSpec> createTestExecuter() {
        return new ExternalTestExecuter(
                new HashSet<>(getFrom().get())
        );
    }

    @Override
    protected TestExecutionSpec createTestExecutionSpec() {
        return new TestExecutionSpec() {
            @Override
            public int hashCode() {
                return super.hashCode();
            }
        };
    }

}
