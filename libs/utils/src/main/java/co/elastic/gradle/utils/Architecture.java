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
package co.elastic.gradle.utils;

import java.util.Map;

public enum Architecture {
    X86_64,
    AARCH64;

    public static Architecture current() {
        String arch = System.getProperty("os.arch").toLowerCase();
        switch (arch) {
            case "amd64":
            case "x86_64":
                return X86_64;
            case "aarch64":
                return AARCH64;
            default:
                throw new IllegalStateException("Unknown co.elastic.gradle.utils.Architecture " + arch);
        }
    }

    public String map(Map<Architecture, String> map) {
        return map.getOrDefault(this, name()).toLowerCase();
    }

    public String dockerName() {
        switch (this) {
            case X86_64:
                return "amd64";
            case AARCH64:
                return "arm64";
            default: throw new IllegalStateException("No docker name for " + this);
        }
    }
}
