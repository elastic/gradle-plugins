/*
 *
 *  * ELASTICSEARCH CONFIDENTIAL
 *  * __________________
 *  *
 *  *  Copyright Elasticsearch B.V. All rights reserved.
 *  *
 *  * NOTICE:  All information contained herein is, and remains
 *  * the property of Elasticsearch B.V. and its suppliers, if any.
 *  * The intellectual and technical concepts contained herein
 *  * are proprietary to Elasticsearch B.V. and its suppliers and
 *  * may be covered by U.S. and Foreign Patents, patents in
 *  * process, and are protected by trade secret or copyright
 *  * law.  Dissemination of this information or reproduction of
 *  * this material is strictly forbidden unless prior written
 *  * permission is obtained from Elasticsearch B.V.
 *
 */

package co.elastic.gradle.dockerbase;

import co.elastic.gradle.utils.RegularFileUtils;
import co.elastic.gradle.utils.docker.DockerUtils;
import co.elastic.gradle.utils.docker.instruction.*;
import org.gradle.api.GradleException;
import org.gradle.api.file.FileSystemOperations;
import org.gradle.process.ExecOperations;

import javax.inject.Inject;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.Stream;

public abstract class DockerDaemonActions {

    private final DockerUtils dockerUtils;
    private final ImageBuildable buildable;
    private final Path workingDir;
    private final UUID uuid;
    private String user;

    @Inject
    public DockerDaemonActions(ImageBuildable buildable) {
        this.dockerUtils = new DockerUtils(getExecOperations());
        this.buildable = buildable;
        this.workingDir = RegularFileUtils.toPath(buildable.getWorkingDirectory());
        uuid = UUID.randomUUID();
    }

    @Inject
    protected abstract ExecOperations getExecOperations();

    @Inject
    protected abstract FileSystemOperations getFilesystemOperations();


    public void checkVersion() {
        ByteArrayOutputStream commandOutput = new ByteArrayOutputStream();
        dockerUtils.exec(spec -> {
            spec.setStandardOutput(commandOutput);
            spec.setEnvironment(Collections.emptyMap());
            spec.commandLine("docker", "version", "--format='{{.Server.Version}}'");
        });
        String dockerVersion = commandOutput.toString(StandardCharsets.UTF_8)
                .trim()
                .replaceAll("'", "");
        int dockerMajorVersion = Integer.parseInt(dockerVersion.split("\\.")[0]);
        if (dockerMajorVersion < 19) {
            throw new IllegalStateException("Docker daemon version must be 19 and above. Currently " + dockerVersion);
        }
    }

    public String dockerFileFromInstructions() {
        return "##########################################################\n" +
               "#                                                        #\n" +
               "#                Auto generated Dockerfile               #\n" +
               "#                                                        #\n" +
               "##########################################################\n" +
               "# syntax = docker/dockerfile:experimental\n" +
               "# Internal UUID: " + uuid + "\n" +
               "# Building " + buildable + "\n\n" +
               buildable.getActualInstructions().stream()
                       .flatMap(this::convertInstallToRun)
                       .map(this::instructionAsDockerFileInstruction)
                       .collect(Collectors.joining("\n"));
    }

    public static Run wrapInstallCommand(ImageBuildable buildable, String command) {
        final OSDistribution distribution = buildable.getOSDistribution().get();
        final boolean requiresCleanLayers = buildable.getRequiresCleanLayers().get();
        return new Run(
                switch (distribution) {
                    case UBUNTU, DEBIAN -> Stream.of(
                            Stream.of(
                                    "cp /var/packages-from-gradle/__META__Packages* /var/packages-from-gradle/Packages.gz"
                            ).filter(s -> requiresCleanLayers),
                            Stream.of(
                                    "rm -f /etc/apt/apt.conf.d/docker-clean",
                                    """
                                                echo 'Binary::apt::APT::Keep-Downloaded-Packages "true";' > /etc/apt/apt.conf.d/docker-dirty
                                            """.trim()
                            ).filter(s -> !requiresCleanLayers),
                            Stream.of("apt-get update", command),
                            Stream.of(
                                    "apt-get clean",
                                    "rm -rf /var/lib/apt/lists/* /tmp/* /var/tmp/* /etc/apt/apt.conf.d/90sslConfig"
                            ).filter(s -> requiresCleanLayers)
                    ).flatMap(s -> s).collect(Collectors.toList());
                    case CENTOS -> Stream.of(
                            Stream.of(
                                    "cd /var/packages-from-gradle/",
                                    "tar -xf __META__repodata*"
                            ).filter(s -> requiresCleanLayers),
                            Stream.of(command),
                            Stream.of(
                                    "yum clean all",
                                    "rm -rf /var/cache/yum /tmp/* /var/tmp/*"
                            ).filter(s -> requiresCleanLayers)
                    ).flatMap(
                            Function.identity()
                    ).collect(Collectors.toList());
                }
        );
    }

    private Stream<? extends ContainerImageBuildInstruction> convertInstallToRun(ContainerImageBuildInstruction instruction) {
        if (instruction instanceof Install install) {
            final String packagesToInstall = install.getPackages().stream()
                    .filter(p -> !p.contains("__META__"))
                    .collect(Collectors.joining(" "));
            return Stream.of(
                    // Install instructions need to be run with root
                    new SetUser("root"),
                    wrapInstallCommand(
                            buildable,
                            switch (buildable.getOSDistribution().get()) {
                                case UBUNTU, DEBIAN -> "apt-get install -y " + packagesToInstall;
                                case CENTOS -> "yum install --setopt=skip_missing_names_on_install=False -y " +
                                                packagesToInstall;
                            }
                    ),
                    // And we restore the user after that
                    new SetUser(user)
            );
        }
        return Stream.of(instruction);
    }

    public String instructionAsDockerFileInstruction(ContainerImageBuildInstruction instruction) {
        if (instruction instanceof From from) {
            return "FROM " + from.getReference().get();
        } else if (instruction instanceof final FromLocalImageBuild fromLocalImageBuild) {
            return "# " + fromLocalImageBuild.otherProjectPath() + "\n" +
                   "FROM " + fromLocalImageBuild.tag().get();
        } else if (instruction instanceof Copy copySpec) {
            return "COPY " + Optional.ofNullable(copySpec.getOwner()).map(s -> "--chown=" + s + " ").orElse("") +
                   workingDir.relativize(getContextDir().resolve(copySpec.getLayer())) + " /";
        } else if (instruction instanceof Run run) {
            String mountOptions = getBindMounts().entrySet().stream()
                    .map(entry -> {
                        // Key is something like: target=/mnt, additional options are possible
                        return "--mount=type=bind," + entry.getKey() +
                               ",source=" + workingDir.relativize(entry.getValue());
                    }).collect(Collectors.joining(" "));
            return "RUN " + mountOptions + "\\\n " +
                   String.join(" && \\ \n\t", run.getCommands());
        } else if (instruction instanceof CreateUser createUser) {
            // Specific case for Alpine and Busybox
            return String.format(
                    """
                            RUN if ! command -v busybox &> /dev/null; then \\\s
                                   groupadd -g %4$s %3$s ; \\\s
                                   useradd -r -s /bin/false -g %4$s --uid %2$s %1$s ; \\\s
                               else \\\s
                                   addgroup --gid %4$s %3$s ; \\\s
                                   adduser -S -s /bin/false --ingroup %3$s -H -D -u %2$s %1$s ; \\\s
                               fi
                            """,
                    createUser.getUsername(),
                    createUser.getUserId(),
                    createUser.getGroup(),
                    createUser.getGroupId()
            );
        } else if (instruction instanceof SetUser) {
            user = ((SetUser) instruction).getUsername();
            return "USER " + user;
        } else if (instruction instanceof Env) {
            return "ENV " + ((Env) instruction).getKey() + "=" + ((Env) instruction).getValue();
        } else if (instruction instanceof HealthCheck healthcheck) {
            return "HEALTHCHECK " + Optional.ofNullable(healthcheck.getInterval()).map(interval -> "--interval=" + interval + " ").orElse("") +
                   Optional.ofNullable(healthcheck.getTimeout()).map(timeout -> "--timeout=" + timeout + " ").orElse("") +
                   Optional.ofNullable(healthcheck.getStartPeriod()).map(startPeriod -> "--start-period=" + startPeriod + " ").orElse("") +
                   Optional.ofNullable(healthcheck.getRetries()).map(retries -> "--retries=" + retries + " ").orElse("") +
                   "CMD " + healthcheck.getCmd();
        } else {
            throw new GradleException("Docker instruction " + instruction + " is not supported for Docker daemon build");
        }
    }

    public Map<String, Path> getBindMounts() {
        final HashMap<String, Path> result = new HashMap<>();

        result.put(
                "readonly=true,target=" + buildable.getDockerEphemeralMount().get(), getDockerEphemeralDir()
        );
        if (buildable.getRequiresCleanLayers().get()) {
            result.put("readonly=true,target=" + switch (buildable.getOSDistribution().get()) {
                        case DEBIAN, UBUNTU -> "/etc/apt/sources.list";
                        case CENTOS -> "/etc/yum.repos.d";
                    },
                    switch (buildable.getOSDistribution().get()) {
                        case DEBIAN, UBUNTU -> getRepositoryEphemeralDir().resolve("sources.list");
                        case CENTOS -> getRepositoryEphemeralDir();
                    }
            );
            result.put("readonly=false,target=/var/packages-from-gradle", getOSPackagesDir());
        }
        return result;
    }

    public Path getWorkingDir() {
        return workingDir;
    }

    public Path getDockerEphemeralDir() {
        return workingDir.resolve("ephemeral/docker");
    }

    public Path getOSPackagesDir() {
        return workingDir.resolve("ephemeral/packages");
    }

    public Path getRepositoryEphemeralDir() {
        return workingDir.resolve("ephemeral/repos");
    }

    public Path getContextDir() {
        return workingDir.resolve("context");
    }

    private void generateEphemeralRepositories() throws IOException {
        final Path listsEphemeralDir = getRepositoryEphemeralDir();

        Files.createDirectories(listsEphemeralDir);

        final URL url = new URL("file:///var/packages-from-gradle");
        final String name = "gradle-configuration";
        try {
            Files.write(
                    listsEphemeralDir.resolve(
                            switch (buildable.getOSDistribution().get()) {
                                case CENTOS -> name + ".repo";
                                case DEBIAN, UBUNTU -> "sources.list";
                            }),
                    switch (buildable.getOSDistribution().get()) {
                        case CENTOS -> List.of(
                                "[" + name + "]",
                                "name=" + name,
                                "baseurl=" + url,
                                "enabled=1",
                                "gpgcheck=0"
                        );
                        case DEBIAN, UBUNTU -> List.of(
                                "deb [trusted=yes] " + url + " /"
                        );
                    }
            );
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }

    }

    public UUID build() throws IOException {
        checkVersion();
        Files.createDirectories(workingDir);
        synchronizeFiles();
        generateEphemeralRepositories();

        {
            final String baseImage = buildable.getActualInstructions().stream()
                    .filter(each -> each instanceof FromImageReference)
                    .map(each -> ((FromImageReference) each).getReference().get())
                    .findFirst()
                    .orElseThrow(() -> new GradleException("A base image is not configured"));
            final ByteArrayOutputStream whoAmIOut = new ByteArrayOutputStream();
            dockerUtils.exec(execSpec -> {
                execSpec.setStandardOutput(whoAmIOut);
                execSpec.commandLine("docker", "run", "--rm", "--entrypoint", "/bin/sh", baseImage, "-c", "'whoami'");
            });
            user = whoAmIOut.toString().trim();
        }

        Path dockerFile = workingDir.resolve("Dockerfile");
        Files.writeString(
                dockerFile,
                dockerFileFromInstructions()
        );

        Files.writeString(
                workingDir.resolve(".dockerignore"),
                "**\n" + Stream.concat(
                                Stream.of(
                                        workingDir.relativize(getContextDir())
                                ),
                                getBindMounts().values().stream()
                                        .map(each -> workingDir.relativize(each).toString())
                        )
                        .map(each -> "!" + each)
                        .collect(Collectors.joining("\n"))
        );

        // We build with --no-cache to make things more straight forward, since we already cache images using Gradle's build cache
        int imageBuild = dockerUtils.exec(spec -> {
            spec.setWorkingDir(dockerFile.getParent().toFile());
            if (System.getProperty("co.elastic.unsafe.use-docker-cache", "false").equals("true")) {
                // This is usefull for development when we don't care about image corectness, but otherwhise dagerous,
                //   e.g. dockerEphemeral content in run commands could lead to incorrect results
                spec.commandLine("docker", "image", "build", "--platform", "linux/" + buildable.getArchitecture().get().dockerName(),
                        "--quiet=false",
                        "--progress=plain",
                        "--iidfile=" + buildable.getImageIdFile().get().getAsFile(), ".", "-t",
                        uuid
                );
            } else {
                spec.commandLine("docker", "image", "build", "--platform", "linux/" + buildable.getArchitecture().get().dockerName(),
                        "--quiet=false",
                        "--no-cache",
                        "--progress=plain",
                        "--iidfile=" + buildable.getImageIdFile().get().getAsFile(), ".", "-t",
                        uuid
                );
            }
            spec.setIgnoreExitValue(true);
        }).getExitValue();
        if (imageBuild != 0) {
            throw new GradleException("Failed to build docker image, see the docker build log in the task output");
        }

        return uuid;
    }

    private void synchronizeFiles() throws IOException {
        Files.createDirectories(getContextDir());
        getFilesystemOperations().sync(spec -> {
                    spec.into(getContextDir());
                    spec.with(buildable.getRootCopySpec());
                }
        );

        final Path dockerEphemeralDir = getDockerEphemeralDir();
        Files.createDirectories(dockerEphemeralDir);
        getFilesystemOperations().sync(copySpec -> {
            copySpec.from(buildable.getDockerEphemeralConfiguration().get());
            copySpec.into(dockerEphemeralDir);
        });

        final Path osPackagesDir = getOSPackagesDir();
        Files.createDirectories(osPackagesDir);
        getFilesystemOperations().sync(copySpec -> {
            copySpec.from(buildable.getOSPackagesConfiguration().get());
            copySpec.into(osPackagesDir);
        });
    }

}
