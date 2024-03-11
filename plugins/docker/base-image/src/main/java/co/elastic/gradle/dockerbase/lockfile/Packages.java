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

import com.fasterxml.jackson.annotation.JsonCreator;
import org.gradle.api.tasks.Nested;
import org.gradle.util.internal.VersionNumber;

import java.io.Serializable;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

public record Packages(
        List<UnchangingPackage> packages
) implements Serializable {

    @JsonCreator
    public Packages {
        if (packages.size() != getUniquePackagesWithMaxVersion(packages).size()) {
            throw new IllegalStateException("Multiple packages have the same name");
        }
    }

    public static List<UnchangingPackage> getUniquePackagesWithMaxVersion(List<UnchangingPackage> packages) {
        final Set<String> nameSet = packages.stream().map(UnchangingPackage::getName)
                .collect(Collectors.toSet());
        return nameSet.stream()
                .map(name -> packages.stream().filter(pkg -> pkg.name().equals(name)).toList())
                .map(list -> list.stream()
                        .max(Comparator.comparing(a -> VersionNumber.parse(a.getVersion())))
                        .orElseThrow(IllegalStateException::new))
                .toList();
    }

    @Nested
    public List<UnchangingPackage> getPackages() {
        return packages();
    }

    public Optional<UnchangingPackage> findByName(String name) {
        return getPackages()
                .stream()
                .filter(each -> each.getName().equals(name))
                .findFirst();
    }

}
