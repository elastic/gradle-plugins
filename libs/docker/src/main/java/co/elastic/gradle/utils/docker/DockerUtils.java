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
package co.elastic.gradle.utils.docker;

import co.elastic.gradle.utils.PrefixingOutputStream;
import co.elastic.gradle.utils.RetryUtils;
import org.gradle.api.Action;
import org.gradle.api.GradleException;
import org.gradle.api.logging.Logger;
import org.gradle.api.logging.Logging;
import org.gradle.api.tasks.TaskExecutionException;
import org.gradle.internal.os.OperatingSystem;
import org.gradle.process.ExecOperations;
import org.gradle.process.ExecResult;
import org.gradle.process.ExecSpec;

import java.io.File;
import java.util.HashMap;
import java.util.Map;

@SuppressWarnings("ClassCanBeRecord") // don't make exec operation visible
public class DockerUtils {

    private static final Logger logger = Logging.getLogger(DockerUtils.class);

    private final ExecOperations execOperations;

    public DockerUtils(ExecOperations execOperations) {
        this.execOperations = execOperations;
    }

    public void pull(String tag) {
        RetryUtils.retry(() -> {
                    try {
                        return exec(spec -> {
                                    spec.commandLine(
                                            "docker",
                                            "--config", System.getProperty("user.home") + File.separator + ".docker",
                                            "pull",
                                            tag
                                    );
                                    spec.setStandardOutput(new PrefixingOutputStream("[docker cli] ", System.out));
                                    spec.setErrorOutput(new PrefixingOutputStream("[docker err] ", System.out));
                                }
                        );
                    } catch (TaskExecutionException e) {
                        throw new GradleException("Error pulling " + tag + " through Docker daemon", e);
                    }
                })
                .maxAttempt(3)
                .onRetryError(e -> logger.lifecycle("failed to pull image, retrying"))
                .exponentialBackoff(1000, 30000)
                .execute();
    }

    public ExecResult exec(Action<? super ExecSpec> action, boolean applyMacWorkaround) {
        Map<String, Object> environment = new HashMap<>();
        // Only pass specific env vars for more reproducible builds
        if (applyMacWorkaround) {
            dockerForMacWorkaround(environment);
        }
        environment.put("LANG", System.getenv("LANG"));
        environment.put("LC_ALL", System.getenv("LC_ALL"));
        environment.put("DOCKER_BUILDKIT", "1");
        return execOperations.exec(spec -> {
            action.execute(spec);
            environment.putAll(spec.getEnvironment());
            spec.setEnvironment(environment);
        });
    }

    public ExecResult exec(Action<? super ExecSpec> action) {
        return exec(action, OperatingSystem.current().isMacOsX());
    }

    /**
     * Adds or updates the PATH environment variable to work around a Docker Desktop for Mac issue.
     * See https://github.com/elastic/cloud/issues/79374 for more context
     *
     * @param env existing environment to inject into
     */
    private static void dockerForMacWorkaround(Map<String, Object> env) {
        env.merge("PATH", "/Applications/Docker.app/Contents/Resources/bin/", (a, b) -> a + File.pathSeparator + b);
    }


}
