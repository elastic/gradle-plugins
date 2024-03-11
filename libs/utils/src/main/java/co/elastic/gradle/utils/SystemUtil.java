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

import org.apache.commons.io.IOUtils;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;

public class SystemUtil {
    /**
     * Get the username for the current Unix user.
     *
     * @return the username for the current Unix user.
     */
    public String getUsername() {
        return System.getProperty("user.name");
    }

    /**
     * Get the UID for the current Unix user.
     *
     * @return the UID for the current Unix user.
     */
    public long getUid() {
        try {
            final Process process = Runtime.getRuntime().exec("id -u");
            return Long.parseLong(IOUtils.toString(process.getInputStream(), StandardCharsets.UTF_8).trim());
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    /**
     * Get the GID for the current Unix user.
     *
     * @return the GID for the current Unix user.
     */
    public long getGid() {
        try {
            final Process process = Runtime.getRuntime().exec("id -g");
            return Long.parseLong(IOUtils.toString(process.getInputStream(), StandardCharsets.UTF_8).trim());
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

}
