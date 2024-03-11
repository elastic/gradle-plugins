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
package co.elastic.gradle.cli.base;

import org.gradle.api.provider.Property;

import java.net.MalformedURLException;
import java.net.URL;

public abstract class BaseCLiExtension {

    public BaseCLiExtension() throws MalformedURLException {
        getPattern().convention("[organisation]/releases/download/[revision]/[module]-[classifier]");
        getBaseURL().convention(new URL("https://artifactory.elastic.dev/artifactory/github-release-proxy"));
    }

    public abstract Property<URL> getBaseURL();

    public abstract Property<String> getPattern();

    public abstract Property<String> getUsername();

    public abstract Property<String> getPassword();

    public abstract Property<String> getVersion();
}
