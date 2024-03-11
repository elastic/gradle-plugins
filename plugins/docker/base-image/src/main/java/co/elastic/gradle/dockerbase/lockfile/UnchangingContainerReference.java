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

import org.gradle.api.tasks.Input;

import java.io.Serializable;
import java.util.Objects;

public record UnchangingContainerReference(
        String repository,
        String tag,
        String digest
) implements Serializable {

    public UnchangingContainerReference(String repository, String tag, String digest) {
        this.repository = Objects.requireNonNull(repository);
        this.tag = Objects.requireNonNull(tag);
        this.digest = Objects.requireNonNull(digest);
    }

    @Input
    public String getRepository() {
        return repository;
    }

    @Input
    public String getTag() {
        return tag;
    }

    @Input
    public String getDigest() {
        return digest;
    }
}
