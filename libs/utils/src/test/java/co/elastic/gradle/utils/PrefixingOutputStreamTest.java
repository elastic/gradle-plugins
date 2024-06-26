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

import org.junit.jupiter.api.Test;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;

import static org.junit.jupiter.api.Assertions.*;

class PrefixingOutputStreamTest {

    @Test
    void testPrefix() throws IOException {
        final ByteArrayOutputStream delegate = new ByteArrayOutputStream();
        new PrefixingOutputStream("[test] ", delegate).write("""
                This is a test
                To show that\r
                each line is prefixed.""".getBytes(StandardCharsets.UTF_8)
        );

        assertEquals("[test] This is a test\n" +
                     "[test] To show that\r\n" +
                     "[test] each line is prefixed.",
                delegate.toString(StandardCharsets.UTF_8)
        );
    }
}