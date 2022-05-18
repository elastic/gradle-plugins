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

import co.elastic.gradle.utils.Architecture;
import co.elastic.gradle.utils.RegularFileUtils;
import co.elastic.gradle.utils.SSLCAChainExtractor;
import co.elastic.gradle.utils.docker.DockerUtils;
import co.elastic.gradle.utils.docker.instruction.*;
import org.gradle.api.GradleException;
import org.gradle.api.file.FileSystemOperations;
import org.gradle.process.ExecOperations;

import javax.inject.Inject;
import java.io.BufferedWriter;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.net.MalformedURLException;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.cert.CertificateEncodingException;
import java.util.*;
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
        final boolean onlyUseMirrorRepositories = buildable.getOnlyUseMirrorRepositories().get();
        final boolean requiresCleanLayers = buildable.getRequiresCleanLayers().get();
        return new Run(
                switch (distribution) {
                    case UBUNTU, DEBIAN -> Stream.of(
                            Stream.of(
                                    "export DEBIAN_FRONTEND=noninteractive",
                                    ". /etc/os-release",
                                    "cp /etc/apt/sources.list.d/certs/90sslConfig /etc/apt/apt.conf.d/",
                                    """
                                            sed -i -e "s#\\$releasever#\\"$VERSION_CODENAME\\"#" /etc/apt/sources.list.d/*.list
                                            """.trim()
                            ),
                            Stream.of(
                                    "rm -f /etc/apt/apt.conf.d/docker-clean",
                                    """
                                        echo 'Binary::apt::APT::Keep-Downloaded-Packages "true";' > /etc/apt/apt.conf.d/docker-dirty
                                    """.trim()).filter(s -> !requiresCleanLayers),
                            Stream.of("mv /etc/apt/sources.list /etc/apt/sources.list.bak").filter(s -> onlyUseMirrorRepositories),
                            Stream.of(
                                    "apt-get update",
                                    command),
                            Stream.of("mv /etc/apt/sources.list.bak /etc/apt/sources.list").filter(s -> onlyUseMirrorRepositories),
                            Stream.of(
                                    "apt-get clean",
                                    "rm -rf /var/lib/apt/lists/* /tmp/* /var/tmp/* /etc/apt/apt.conf.d/90sslConfig"
                            ).filter(s -> requiresCleanLayers)
                    ).flatMap(s -> s).collect(Collectors.toList());
                    case CENTOS -> List.of(
                            command,
                            "yum clean all",
                            "rm -rf /var/cache/yum /tmp/* /var/tmp/*"
                    );
                }
        );
    }

    private Stream<? extends ContainerImageBuildInstruction> convertInstallToRun(ContainerImageBuildInstruction instruction) {
        if (instruction instanceof Install install) {
            return Stream.of(
                    // Install instructions need to be run with root
                    new SetUser("root"),
                    wrapInstallCommand(
                            buildable,
                            switch (buildable.getOSDistribution().get()) {
                                case UBUNTU, DEBIAN -> "apt-get install -y " + String.join(" ", install.getPackages());
                                case CENTOS -> "yum install -y " + String.join(" ", install.getPackages());
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
        String reposPath = switch (buildable.getOSDistribution().get()) {
            case DEBIAN, UBUNTU -> "/etc/apt/sources.list.d";
            case CENTOS -> "/etc/yum.repos.d";
        };
        String authPath = switch (buildable.getOSDistribution().get()) {
            case DEBIAN, UBUNTU -> "/etc/apt/auth.conf.d";
            case CENTOS -> "/etc/auth";
        };

        return Map.of(
                "readonly=false,target=" + buildable.getDockerEphemeralMount().get(), getDockerEphemeralDir(),
                "readonly=false,target=" + reposPath, getRepositoryEphemeralDir(),
                "readonly=true,target=" + authPath, getAuthEphemeralDir()
        );
    }

    public Path getWorkingDir() {
        return workingDir;
    }

    public Path getDockerEphemeralDir() {
        return workingDir.resolve("ephemeral/docker");
    }

    public Path getRepositoryEphemeralDir() {
        return workingDir.resolve("ephemeral/repos");
    }

    public Path getAuthEphemeralDir() {
        return workingDir.resolve("ephemeral/auth");
    }

    public Path getContextDir() {
        return workingDir.resolve("context");
    }

    private void generateEphemeralRepositories() throws IOException {
        final Path listsEphemeralDir = getRepositoryEphemeralDir();
        final Path authEphemeralDir = getAuthEphemeralDir();

        Files.createDirectories(listsEphemeralDir);
        Files.createDirectories(authEphemeralDir);

        switch (buildable.getOSDistribution().get()) {
            case UBUNTU, DEBIAN -> {
                writeAPTMirrorConfig(listsEphemeralDir, authEphemeralDir);
            }
            case CENTOS -> {
                // CentOS has this in the repo file
            }
        }

        buildable.getMirrorRepositories().get()
                .forEach(repo -> {
                    final URL url = repo.getUrl().get();
                    final URL safeUrl;
                    try {
                        safeUrl = new URL(url.toString().replace(url.getUserInfo() + "@", ""));
                    } catch (MalformedURLException e) {
                        throw new IllegalStateException("Invalid url", e);
                    }
                    try {
                        Files.write(
                                listsEphemeralDir.resolve(
                                        switch (buildable.getOSDistribution().get()) {
                                            case CENTOS -> repo.getName() + ".repo";
                                            case DEBIAN, UBUNTU -> repo.getName() + ".list";
                                        }),
                                switch (buildable.getOSDistribution().get()) {
                                    case CENTOS -> {
                                        final Optional<String[]> credentials = Optional.ofNullable(
                                                repo.getUrl().get().getUserInfo()
                                        ).map(each -> each.split(":"));
                                        @SuppressWarnings("OptionalGetWithoutIsPresent") final String username =
                                                credentials.isPresent() ?
                                                        ("username=" + credentials.map(cred -> cred[0]).get() + "\n") :
                                                        "";
                                        @SuppressWarnings("OptionalGetWithoutIsPresent") final String password =
                                                credentials.isPresent() ?
                                                        "password=" + credentials.map(cred -> cred[1]).get() + "\n" :
                                                        "";
                                        yield List.of(
                                                "[" + repo.getName() + "]",
                                                "name=" + repo.getName(),
                                                username +
                                                password +
                                                "baseurl=" + safeUrl,
                                                "enabled=1",
                                                "gpgcheck=0"
                                        );
                                    }
                                    case DEBIAN, UBUNTU -> List.of(
                                            "deb [trusted=yes] " + safeUrl
                                    );
                                }
                        );
                    } catch (IOException e) {
                        throw new UncheckedIOException(e);
                    }
                });
    }

    private void writeAPTMirrorConfig(Path listsEphemeralDir, final Path authEphemeralDir) throws IOException {
        final Set<List<String>> hosts = buildable.getMirrorRepositories().get().stream()
                .map(each -> List.of(each.getUrl().get().getHost(), each.getUrl().get().getUserInfo()))
                .collect(Collectors.toSet());
        final Path certsDir = listsEphemeralDir.resolve("certs");

        Files.createDirectories(certsDir);
        try (BufferedWriter mirrorConfWriter = Files.newBufferedWriter(authEphemeralDir.resolve("mirror.conf"));
             BufferedWriter sslConfWriter = Files.newBufferedWriter(listsEphemeralDir.resolve("certs/90sslConfig"));
             BufferedWriter sslCertsWriter = Files.newBufferedWriter(certsDir.resolve("mirror.pem"))) {
            for (List<String> hostAndAuth : hosts) {
                final String[] userinfo = hostAndAuth.get(1).split(":");
                final String hostName = hostAndAuth.get(0);

                mirrorConfWriter.write(
                        String.format("machine %s login %s password %s\n", hostName, userinfo[0], userinfo[1]));

                final URL anyUrl = buildable.getMirrorRepositories().get().iterator().next().getUrl().get();
                if (anyUrl.getProtocol().toLowerCase(Locale.ROOT).equals("https")) {
                    // Pass in the CA chain to apt so it can still access https repos, even without ca-certificates installed
                    // The CA chain is validated on the host, so it's ok to trust it in the container.
                    sslConfWriter.write(String.format("""
                            Acquire::https::%s {
                              CaInfo      "/etc/apt/sources.list.d/certs/mirror.pem";
                            }
                            """, hostName));

                    sslCertsWriter.write(SSLCAChainExtractor.extract(
                                    anyUrl.getHost(), anyUrl.getPort() == -1 ? anyUrl.getDefaultPort() : anyUrl.getPort()
                            ).stream()
                            .map(each -> {
                                try {
                                    return "-----BEGIN CERTIFICATE-----\n" +
                                            Base64.getEncoder().encodeToString(each.getEncoded()) +
                                            "\n-----END CERTIFICATE-----";
                                } catch (CertificateEncodingException e1) {
                                    throw new GradleException("Can't encode certificates", e1);
                                }
                            })
                            .collect(Collectors.joining("\n")));
                }
            }
        }
    }

    public UUID build() throws IOException {
        checkVersion();
        Files.createDirectories(workingDir);
        synchronizeLayersAndEphemeralConfiguration();
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
                spec.commandLine("docker", "image", "build", "--platform", "linux/" + Architecture.current().dockerName(),
                        "--quiet=false",
                        "--progress=plain",
                        "--iidfile=" + buildable.getImageIdFile().get().getAsFile(), ".", "-t",
                        uuid
                );
            } else {
                spec.commandLine("docker", "image", "build", "--platform", "linux/" + Architecture.current().dockerName(),
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

    private void synchronizeLayersAndEphemeralConfiguration() throws IOException {
        Files.createDirectories(getContextDir());
        getFilesystemOperations().sync(spec -> {
                    spec.into(getContextDir());
                    spec.with(buildable.getRootCopySpec());
                }
        );

        final Path dockerEphemeralDir = getDockerEphemeralDir();
        Files.createDirectories(dockerEphemeralDir);
        getFilesystemOperations().sync(copySpec -> {
            copySpec.from(buildable.getDockerEphemeralConfiguration());
            copySpec.into(dockerEphemeralDir);
        });
    }

}
