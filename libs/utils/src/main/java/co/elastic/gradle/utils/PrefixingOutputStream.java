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

import java.io.IOException;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;

public class PrefixingOutputStream extends OutputStream {

    private final byte[] prefix;
    private boolean firstByteWritten = false;
    private final OutputStream delegate;

    public PrefixingOutputStream(String prefix, OutputStream delegate) {
        this.prefix = prefix.getBytes(StandardCharsets.UTF_8);
        this.delegate = delegate;
    }

    @Override
    public void write(int b) throws IOException {
        if (!firstByteWritten) {
            delegate.write(prefix);
            firstByteWritten = true;
        }
        delegate.write(b);
        if (b == '\n') {
            delegate.write(prefix);
        }
    }

}
