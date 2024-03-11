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

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.UncheckedIOException;


public class Resources {
    public static String getResourceContents(String resourcePath) {
      try (
          BufferedReader reader = new BufferedReader(new InputStreamReader(Resources.class.getResourceAsStream(resourcePath)))
      ) {
          StringBuilder b = new StringBuilder();
          for (String line = reader.readLine(); line != null; line = reader.readLine()) {
              if (b.length() != 0) {
                  b.append('\n');
              }
              b.append(line);
          }

          return b.toString();
      } catch (IOException e) {
          throw new UncheckedIOException("Error trying to read classpath resource: " + resourcePath, e);
      }
    }
  }