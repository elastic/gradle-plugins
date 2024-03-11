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
package co.elastic.gradle.sandbox;

import co.elastic.gradle.utils.docker.DockerUtils;
import org.gradle.api.Action;
import org.gradle.api.GradleException;
import org.gradle.api.Task;
import org.gradle.api.tasks.*;
import org.gradle.process.ExecResult;

import java.io.File;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.*;
import java.util.stream.Stream;

@CacheableTask
abstract public class SandboxExecTask extends SandboxExecBaseTask {

    protected final List<File> runs = new ArrayList<>();
    private final Path pathDir;
    private final Set<String> systemBinaries = new HashSet<>();

    public SandboxExecTask() {
        super();
        pathDir = sandbox.resolve(".bin");
    }

    public void runsDockerCompose() {
        Stream.of(
                "docker-compose",
                "docker-compose-v1"
        ).flatMap(binaryName -> Stream.of(
                                Paths.get("/", "usr", "bin"),
                                Paths.get("/", "usr", "local", "bin")
                        )
                        .map(dir -> dir.resolve(binaryName).toAbsolutePath())
                        .filter(Files::exists)
                        .findFirst()
                        .stream()
        ).forEach(path -> {
            this.doLast(new Action<Task>() {
                @Override
                public void execute(Task t) {
                    getLogger().lifecycle("Using docker-compose {}", path);
                }
            });
            runs(path.toFile());
        });
    }

    public void runs(File executable) {
        runs.add(executable);
    }

    @SuppressWarnings("unused")
    public void runs(File... executable) {
        Arrays.stream(executable).forEach(this::runs);
    }

    @InputFiles
    @PathSensitive(PathSensitivity.NONE)
    public List<File> getRuns() {
        return List.copyOf(runs);
    }

    @Input
    public Set<String> getSystemBinaries() {
        return Set.copyOf(this.systemBinaries);
    }

    private Path resolveSystemBinary(String systemBinary) {
        Path usrBin = Paths.get("/usr/bin").resolve(systemBinary);
        if (Files.exists(usrBin)) {
            return usrBin;
        }
        Path bin = Paths.get("/bin").resolve(systemBinary);
        if (Files.exists(bin)) {
            return bin;
        }
        throw new GradleException("Could not find an executable at `" + usrBin + "` or `" + bin +"`");
    }

    @SuppressWarnings("unused")
    public void runsSystemBinary(String... systemBinaries) {
        for (String systemBinary : systemBinaries) {
            resolveSystemBinary(systemBinary);
            this.systemBinaries.add(systemBinary);
        }
    }

    @Override
    protected ExecResult doExec() {
        createSandboxPathDir();
        if (inputFiles.isEmpty()) {
            throw new IllegalArgumentException("No inputs specified for sandbox " + getPath());
        }
        Map<String, String> env = new HashMap<>(environment);
        env.put("PATH", pathDir.toAbsolutePath().toString());

        // Run this with the docker utils to benefit from the docker for mac workaround in case we are running docker or
        // docker-compose.
        return new DockerUtils(getExecOperations()).exec(spec -> {
            spec.setWorkingDir(workingDirectory);
            spec.setEnvironment(env);
            spec.setCommandLine(commandLine);
            spec.setIgnoreExitValue(true);
        });
    }

    private void createSandboxPathDir() {
        try {
            Files.createDirectories(pathDir);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
        if (!runs.isEmpty()) {
            symlinkToPathDir(runs);
        }
        if (!systemBinaries.isEmpty()) {
            symlinkToPathDir(
                    systemBinaries.stream()
                            .map(this::resolveSystemBinary)
                            .map(Path::toFile)
                            .toList()
            );
        }
    }

    private void symlinkToPathDir(final List<File> binaries) {
        List<File> directoryRuns = binaries.stream().filter(File::isDirectory).toList();
        if (!directoryRuns.isEmpty()) {
            throw new GradleException("Configured `runs` need to be files but these are directories instead: " + directoryRuns);
        }
        List<File> nonExistentRuns = binaries.stream().filter(each -> !each.exists()).toList();
        if (!nonExistentRuns.isEmpty()) {
            throw new GradleException("Configured `runs` don't exist: " + nonExistentRuns);
        }
        binaries.stream()
                .map(File::toPath)
                .forEach(runs -> {
                    try {
                        final Path link = pathDir.resolve(runs.getFileName());
                        if (Files.exists(link)) {
                            // We re-create te path dir with retries so the targets might exist
                            Files.delete(link);
                        }
                        Files.createSymbolicLink(link, runs);
                    } catch (IOException e) {
                        throw new UncheckedIOException(e);
                    }
                });
    }


}
