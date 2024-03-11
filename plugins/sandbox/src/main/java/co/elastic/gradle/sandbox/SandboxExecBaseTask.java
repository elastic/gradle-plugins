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

import co.elastic.gradle.utils.GradleUtils;
import co.elastic.gradle.utils.XunitCreatorTask;
import org.gradle.api.DefaultTask;
import org.gradle.api.GradleException;
import org.gradle.api.file.ConfigurableFileTree;
import org.gradle.api.file.FileCollection;
import org.gradle.api.file.FileTree;
import org.gradle.api.internal.file.FileOperations;
import org.gradle.api.internal.file.UnionFileCollection;
import org.gradle.api.internal.provider.PropertyFactory;
import org.gradle.api.provider.Provider;
import org.gradle.api.provider.ProviderFactory;
import org.gradle.api.tasks.*;
import org.gradle.process.ExecOperations;
import org.gradle.process.ExecResult;

import javax.inject.Inject;
import java.io.File;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.*;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * Run a command in a sandbox with caching enabled
 *
 * <b>NOTE:</b> This should be used with deterministic commands only!
 * e.x.
 * <code>
 * tasks.register&lt;SandboxExecTask&gt;("example") {
 * reads("src/foo.txt")
 * writesFile(file("out.txt"))
 * writesDir(file("$buildDir/results"))
 * commandLine("example", "src/foo.txt")
 * }
 * </code>
 */
@CacheableTask
public abstract class SandboxExecBaseTask extends DefaultTask implements XunitCreatorTask {

    protected final List<String> commandLine = new ArrayList<>();
    protected final Map<String, String> environment = new TreeMap<>();
    protected final Path rootProjectPath;
    protected final Path currentProjectPath;
    protected FileCollection inputFiles;
    protected FileCollection nonInputFiles;
    protected final List<File> outputFiles = new ArrayList<>();
    protected final List<File> outputDirs = new ArrayList<>();
    protected int maxTries = 1;

    protected final File marker;
    protected final Path baseDir;
    protected final Path sandbox;
    protected Path workingDirectory;
    private Path outputsRoot;

    public SandboxExecBaseTask() {
        super();
        if (!getProject().getPlugins().hasPlugin(SandboxPlugin.class)) {
            throw new GradleException("Task " + getPath() + " can only be used if the sandbox plugin is applied");
        }
        inputFiles = getProject().files();
        nonInputFiles = getProject().files();
        marker = new File(getProject().getBuildDir(), getName() + "-" + getClass().getSimpleName() + ".marker");
        outputFiles.add(marker);
        baseDir = getProject().getBuildDir().toPath()
                .resolve("sandbox")
                .resolve(getName());
        sandbox = getRandomPath(baseDir);
        currentProjectPath = getProject().getProjectDir().toPath();
        rootProjectPath = getProject().getRootProject().getProjectDir().toPath();
        workingDirectory = sandbox.resolve(rootProjectPath.relativize(currentProjectPath));
        outputsRoot = baseDir.resolve("outputs");
    }



    @Inject
    protected abstract PropertyFactory getPropertyFactory();

    @Inject
    protected abstract ExecOperations getExecOperations();

    @Inject
    protected abstract FileOperations getFileOperations();

    @Inject
    protected abstract ProviderFactory getProviderFactory();

    public void setCommandLine(List<String> commandLine) {
        this.commandLine.clear();
        this.commandLine.addAll(commandLine);
    }

    public void setWorkingDir(String relativePath) {
        workingDirectory = sandbox.resolve(relativePath);
    }

    @Input
    public String getWorkingDir() {
        // We treat this as an input so the random part of the sandbox doesn't invalidate the cache
        return sandbox.relativize(workingDirectory).toString();
    }

    @Input
    public List<String> getCommandLine() {
        return Collections.unmodifiableList(commandLine);
    }

    @Input
    public Map<String, String> getEnvironment() {
        return Collections.unmodifiableMap(environment);
    }

    public void environment(String key, String value) {
        environment.put(key, value);
    }

    public void environment(Map<String, String> env) {
        environment.putAll(env);
    }

    @SuppressWarnings("unchecked")
    public void reads(Object file) {
        if (file instanceof FileCollection) {
            inputFiles = inputFiles.plus((FileCollection) file);
        } else if (file instanceof Map) {
            Map<File, FileCollection> fileMap = (Map<File, FileCollection>) file;
            inputFiles = inputFiles.plus(getProject().files(fileMap.keySet()));
            nonInputFiles = fileMap.values().stream().reduce(nonInputFiles, FileCollection::plus);
        } else {
            inputFiles = inputFiles.plus(getProject().files(file));
        }
    }

    @InputFiles
    @PathSensitive(PathSensitivity.RELATIVE)
    public FileCollection getReads() {
        return inputFiles;
    }

    public void writes(Object file) {
        if (file instanceof File) {
            outputFiles.add((File) file);
        } else if (file instanceof ConfigurableFileTree) {
            outputDirs.add(((ConfigurableFileTree) file).getDir());
        } else {
            outputFiles.add(getProject().file(file));
        }
    }

    public void maxTries(int maxTries) {
        if (maxTries == 0) {
            throw new GradleException("Max tries can't be 0");
        }
        this.maxTries = maxTries;
    }

    @OutputFiles
    public List<File> getOutputFiles() {
        return outputFiles;
    }

    @OutputFiles
    @Override
    public Provider<Collection<File>> getXunitFiles() {
        return getProviderFactory().provider(
                () -> ((FileTree) getProject().fileTree(outputsRoot.toFile()).include("**/*.xml")).getFiles()
        );
    }

    @OutputDirectories
    public List<File> getOutputDirs() {
        return outputDirs;
    }

    @TaskAction
    public void taskAction() throws IOException {

        // Make sure to delete previous runs so we don't use infinite disk space
        getFileOperations().delete(baseDir);

        // The result of the command e.x. exit code zero is a valid task output so we store a marker file to have some
        // output to work with caching.
        Path markerFile = sandbox.resolve(rootProjectPath.relativize(marker.toPath()));
        Files.createDirectories(markerFile.getParent());
        Files.write(markerFile, new byte[]{});

        linkFilesIntoSandbox();

        ExecResult exec;
        int tryNr = 1;
        do {
            environment("GRADLE_SANDBOX_TRY_NR", String.valueOf(tryNr));
            exec = doExec();
            if (exec.getExitValue() != 0 && tryNr != maxTries) {
                getLogger().warn("\n== Command failed on try {}, but {} are allowed, going to retry ==\n", tryNr, maxTries);
            }

            if (exec.getExitValue() == 0) {
                List<Path> missing = Stream.concat(outputFiles.stream(), outputDirs.stream())
                        .map(this::getPathInSandbox)
                        .filter(path -> !Files.exists(path))
                        .collect(Collectors.toList());
                if (!missing.isEmpty()) {
                    throw new IllegalArgumentException(
                            "Command succeeded, but expected output(s) doesn't exist in sandbox: \n" +
                                    missing.stream()
                                            .map(each -> "  -" + sandbox.relativize(each))
                                            .collect(Collectors.joining("\n"))
                    );
                }
            }
            final Path outputsTry = outputsRoot.resolve("try-" + tryNr);
            Files.createDirectories(outputsTry);
            linkFilesOutOfSandbox(outputsTry);
            if (exec.getExitValue() == 0) {
                linkFilesOutOfSandbox(rootProjectPath);
                break;
            }
            tryNr++;
        } while (tryNr <= maxTries);
        if (exec.getExitValue() != 0) {
            throw new IllegalStateException(
                    "Sandbox exec " + getPath() + " failed with exit code " + exec.getExitValue() +
                            ".\nCheck the task output for details."
            );
        }
    }

    protected abstract ExecResult doExec();

    private Path getRandomPath(Path baseDir) {
        Random random = new Random();
        return baseDir.resolve(
                random.ints(random.nextInt(5), 10, 99)
                        .boxed()
                        .map(String::valueOf)
                        .collect(Collectors.joining(File.separator))
        );
    }

    private void linkFilesIntoSandbox() {
        // Gradle's FileTree.getFiles implementation discards empty dirs so we hook into internals a bit and do our own
        //   listing of directories here to get the empty dirs
        Stream.of(inputFiles, nonInputFiles)
                .flatMap(this::getPathStream)
                .forEach(this::linkPath);
    }

    private void linkPath(Path source) {
        if (!Files.exists(source)) {
            throw new IllegalArgumentException("No such file:" + source);
        }
        try {
            // We can have inputs from other projects, so we construct paths relative to the root project
            Path relativeDestination = rootProjectPath.relativize(source);
            Path destination = sandbox.resolve(relativeDestination);
            if (Files.isDirectory(source)) {
                Files.createDirectories(destination);
            } else {
                if (Files.isSymbolicLink(source)) {
                    // A relative symbolic link needs to be kept as it can be relied on for behavior,
                    // e.g. `require` in some versions of node seems to care.
                    // since we keep the directory structure these should continue to work
                    Files.createDirectories(destination.getParent());
                    Files.copy(source, destination, LinkOption.NOFOLLOW_LINKS, StandardCopyOption.REPLACE_EXISTING);
                } else {
                    createHardlink(source, destination);
                }
            }
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    private Stream<Path> getPathStream(FileCollection fileCollection) {
        if (fileCollection instanceof UnionFileCollection) {
            return ((UnionFileCollection) fileCollection).getSources().stream().flatMap(this::getPathStream);
        } else if (fileCollection instanceof ConfigurableFileTree) {
            Set<Path> paths = new HashSet<>();
            ConfigurableFileTree configurableFileTree = (ConfigurableFileTree) fileCollection;
            configurableFileTree.visit(visitDetails -> paths.add(visitDetails.getFile().toPath()));
            return paths.stream();
        } else {
            return fileCollection.getFiles().stream().map(File::toPath);
        }
    }

    private void linkFilesOutOfSandbox(Path outputRoot) {
        List<Path> incorrectFiles = outputFiles.stream()
                .map(this::getPathInSandbox)
                .filter(Files::isDirectory)
                .collect(Collectors.toList());
        List<Path> incorrectDirs = outputDirs.stream()
                .map(this::getPathInSandbox)
                .filter(Files::isRegularFile)
                .collect(Collectors.toList());
        if (!(incorrectFiles.isEmpty() && incorrectDirs.isEmpty())) {
            String errorMessage = "";
            if (!incorrectFiles.isEmpty()) {
                errorMessage = "The following outputs should be files, but are in fact directories:" +
                        GradleUtils.listPathsRelativeToProject(getProject(), incorrectFiles) + "\n";
            }
            if (!incorrectDirs.isEmpty()) {
                errorMessage = "The following outputs should be directories, but are in fact files:" +
                        GradleUtils.listPathsRelativeToProject(getProject(), incorrectDirs) + "\n";
            }
            throw new IllegalArgumentException(errorMessage);
        }

        Stream.concat(outputFiles.stream(), outputDirs.stream())
                .map(this::getPathInSandbox)
                // Recurse into directories to get all output files
                .map(each -> {
                    if (Files.isDirectory(each)) {
                        return getProject().fileTree(each);
                    } else {
                        return getProject().files(each);
                    }
                })
                .flatMap(each -> each.getFiles().stream())
                .map(File::toPath)
                // We already check for all outputs to exist in case the command is successful, but we need to skip
                // non existing ones here to account for missing files in case the command failed so we at least get
                // some of the outputs.
                .filter(Files::exists)
                .forEach(source -> {
                    // we converted the output paths to point to the sandbox, so we have to work back the destination outside of it
                    Path relativeDestination = sandbox.relativize(source);
                    Path destination = outputRoot.resolve(relativeDestination);
                    if (Files.isDirectory(destination)) {
                        throw new IllegalArgumentException("Unexpected directory: " + source);
                    }
                    createHardlink(source, destination);
                });
    }

    private Path getPathInSandbox(File each) {
        Path relativeDestination = rootProjectPath.relativize(each.toPath());
        return sandbox.resolve(relativeDestination);
    }

    private void createHardlink(Path source, Path destination) {
        if (Files.exists(destination)) {
            try {
                Files.delete(destination);
            } catch (IOException e) {
                throw new UncheckedIOException("Can't remove existing file " + destination, e);
            }
        }
        try {
            Files.createDirectories(destination.getParent());
        } catch (IOException e) {
            throw new UncheckedIOException("Can't create directory " + destination.getParent(), e);
        }
        try {
            Files.createLink(destination, source);
        } catch (IOException e) {
            // Note does not work for network drives, e.g. Vagrant
            throw new UncheckedIOException(
                    "Failed to create hard link " + destination + " pointing to " + source,
                    e
            );
        }
    }

}
