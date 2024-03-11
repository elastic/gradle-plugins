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

import org.gradle.api.Action;
import org.gradle.api.file.ConfigurableFileTree;
import org.gradle.api.file.FileTree;
import org.gradle.api.file.ProjectLayout;
import org.gradle.api.model.ObjectFactory;
import org.gradle.api.plugins.ExtensionAware;
import org.gradle.api.provider.ListProperty;
import org.gradle.api.provider.ProviderFactory;

import javax.inject.Inject;
import java.util.ArrayList;
import java.util.List;


public abstract class LicenseHeadersExtension implements ExtensionAware {

    public void check(ConfigurableFileTree files, Action<LicenseHeaderConfig> config) {
        final ListProperty<LicenseHeaderConfig> configsProperty = getConfigs();
        final LicenseHeaderConfig licenseHeaderConfig = getObjectFactory().newInstance(LicenseHeaderConfig.class);

        config.execute(licenseHeaderConfig);

        // Default excludes
        FileTree filteredFiles = (FileTree) files
                .exclude(getProjectLayout().getBuildDirectory().get().getAsFile().getName())
                .exclude(".gradle");

        licenseHeaderConfig.getFiles().set(
                getProviderFactory()
                        .provider(() -> filteredFiles.matching(licenseHeaderConfig.getFilter()).getFiles())
        );

        final ArrayList<LicenseHeaderConfig> configsValue = new ArrayList<>(
                List.of(licenseHeaderConfig)
        );
        if (configsProperty.isPresent()) {
            configsValue.addAll(configsProperty.get());
        }
        configsProperty.set(configsValue);
    }

    public void check(ConfigurableFileTree files) {
        check(files, config -> {
        });
    }

    public abstract ListProperty<LicenseHeaderConfig> getConfigs();

    @Inject
    protected abstract ObjectFactory getObjectFactory();

    @Inject
    protected abstract ProviderFactory getProviderFactory();

    @Inject
    protected abstract ProjectLayout getProjectLayout();

}
