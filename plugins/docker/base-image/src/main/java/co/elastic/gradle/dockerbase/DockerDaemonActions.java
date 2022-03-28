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
import co.elastic.gradle.utils.docker.instruction.CreateUser;
import co.elastic.gradle.utils.docker.DockerUtils;
import co.elastic.gradle.utils.docker.instruction.*;
import org.apache.commons.compress.archivers.tar.TarArchiveEntry;
import org.apache.commons.compress.archivers.tar.TarArchiveInputStream;
import org.apache.commons.compress.compressors.zstandard.ZstdCompressorInputStream;
import org.apache.commons.compress.compressors.zstandard.ZstdCompressorOutputStream;
import org.apache.commons.io.IOUtils;
import org.gradle.api.GradleException;
import org.gradle.process.ExecOperations;
import org.gradle.process.ExecResult;

import java.io.*;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.*;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.stream.Collectors;
import java.util.stream.Stream;

public class DockerDaemonActions {

    public static String EPHEMERAL_MOUNT = "/mnt/ephemeral";
    public static final String CONTEXT_FOLDER = "context";
    private final DockerUtils dockerUtils;

    public DockerDaemonActions(ExecOperations execOperations) {
        this.dockerUtils = new DockerUtils(execOperations);
    }


    /**
     * Reads a Zstandard compressed or plain TAR docker image into an InputStream
     *
     * @param imageArchive The path to the image TAR
     */
    public static InputStream readDockerImage(Path imageArchive) throws IOException {
        InputStream imageStream = new BufferedInputStream(Files.newInputStream(imageArchive, StandardOpenOption.READ));
        byte[] magicBytes = new byte[4];
        imageStream.mark(4);
        if (imageStream.read(magicBytes) != 4) {
            throw new IOException("Failed to read magic bytes");
        }
        imageStream.reset();
        int magicNumber = ByteBuffer.wrap(magicBytes).order(ByteOrder.LITTLE_ENDIAN).getInt();
        // https://tools.ietf.org/html/rfc8478
        if (magicNumber == 0xFD2FB528) {
            return new ZstdCompressorInputStream(imageStream);
        } else {
            return imageStream;
        }
    }

    /**
     * Extracts a Zstandard compressed or plain TAR docker image into the destination directory
     *
     * @param tarPath     The path of the image TAR
     * @param destination The destination directory
     * @param filter      A predicate to limit extraction of entries that are present in the TAR
     */
    public static void extractDockerImage(Path tarPath, Path destination, Predicate<TarArchiveEntry> filter) throws IOException {
        try (InputStream imageStream = readDockerImage(tarPath)) {
            extractDockerImage(imageStream, destination, filter);
        }
    }

    /**
     * Extracts a TAR docker image stream into the destination directory
     *
     * @param imageStream The docker image stream
     * @param destination The destination directory
     * @param filter      A predicate to limit extraction of entries that are present in the TAR
     */
    public static void extractDockerImage(InputStream imageStream, Path destination, Predicate<TarArchiveEntry> filter) throws IOException {
        TarArchiveInputStream tarStream = new TarArchiveInputStream(imageStream);
        TarArchiveEntry entry;
        while ((entry = tarStream.getNextTarEntry()) != null) {
            Path entryPath = destination.resolve(entry.getName());
            if (filter.test(entry)) {
                if (entry.isDirectory()) {
                    Files.createDirectories(entryPath);
                } else {
                    if (entryPath.getParent() != null) {
                        Files.createDirectories(entryPath.getParent());
                    }
                    try (OutputStream out = new BufferedOutputStream(Files.newOutputStream(entryPath))) {
                        IOUtils.copy(tarStream, out);
                    }
                }
            }
        }
    }


    public List<String> installCommands(Package.PackageInstaller installer, List<Package> packages) {
        List<String> result = new ArrayList<>();
        switch (installer) {
            // temporary workaround (https://github.com/elastic/cloud/issues/70170)
            case CONFIG:
                result.addAll(packages.stream().map(Package::getName).toList());
                break;
            case YUM:
                result.addAll(installCommands(
                        new Install(
                                Install.PackageInstaller.YUM,
                                new ArrayList<>(),
                                packages.stream().map(p -> new Install.Package(p.getName(), p.getVersion())).collect(Collectors.toList()))
                ));
                break;
            case APT:
                result.addAll(installCommands(
                        new Install(
                                Install.PackageInstaller.APT,
                                new ArrayList<>(),
                                packages.stream().map(p -> new Install.Package(p.getName(), p.getVersion())).collect(Collectors.toList()))));
                break;
            case APK:
                result.addAll(installCommands(
                        new Install(
                                Install.PackageInstaller.APK,
                                new ArrayList<>(),
                                packages.stream().map(p -> new Install.Package(p.getName(), p.getVersion())).collect(Collectors.toList()))));
                break;
            default:
                throw new GradleException("Unexpected value for package installer generating command for " + installer);
        }
        return result;
    }



    public void checkVersion() throws IOException {
        ByteArrayOutputStream commandOutput = new ByteArrayOutputStream();
        dockerUtils.exec(spec -> {
            spec.setStandardOutput(commandOutput);
            spec.setEnvironment(Collections.emptyMap());
            spec.commandLine("docker", "version", "--format='{{.Server.Version}}'");
        });
        String dockerVersion = new String(commandOutput.toByteArray(), StandardCharsets.UTF_8).trim().replaceAll("'", "");
        ;
        int dockerMajorVersion = Integer.parseInt(dockerVersion.split("\\.")[0]);
        if (dockerMajorVersion < 19) {
            throw new IllegalStateException("Docker daemon version must be 19 and above. Currently " + dockerVersion);
        }
    }

    public String dockerFileFromInstructions(List<ContainerImageBuildInstruction> instructions, Path workingDirectory) {
        Function<ContainerImageBuildInstruction, String> partialApply = (ContainerImageBuildInstruction i) -> instructionAsDockerFileInstruction(i, workingDirectory);
        return "#############################\n" +
                "#                           #\n" +
                "# Auto generated Dockerfile #\n" +
                "#                           #\n" +
                "#############################\n\n" +
                "# syntax = docker/dockerfile:experimental" +
                instructions.stream().flatMap(i -> {
                            if (i instanceof From || i instanceof FromLocalImageBuild) {
                                // There's an unfortunate Catch-22 where we need ca-certificates to use artifactory,
                                // but can't get the package from artifactory because we need ca-certificates first.

                                // This *hack* will determine if the image will use APT and if so install any defined
                                // `ca-certificates` package before anything else, and without custom repositories.
                                List<Install.Package> aptPackages = instructions.stream()
                                        .filter(install -> (install instanceof Install) &&
                                                Install.PackageInstaller.APT.equals(((Install) install).getInstaller()))
                                        .flatMap(install -> ((Install) install).getPackages().stream())
                                        .collect(Collectors.toList());

                                // The install-instruction is injected just after the FROM-instruction
                                return Stream.concat(Stream.of(i),
                                        aptPackages.stream()
                                                .filter(p -> List.of("ca-certificates", "ca-certificates:all").contains(p.getName()))
                                                .findFirst()
                                                .stream()
                                                .map(p -> new Install(
                                                        Install.PackageInstaller.APT,
                                                        new ArrayList<>(),
                                                        Collections.singletonList(p))));
                            } else {
                                return Stream.of(i);
                            }
                        })
                        .map(partialApply)
                        .reduce("", (acc, value) -> acc + "\n\n" + value);
    }

    public String instructionAsDockerFileInstruction(ContainerImageBuildInstruction instruction, Path workingDirectory) {
        if (instruction instanceof From from) {
            return "FROM " + from.getImage() + ":" + from.getVersion() + Optional.ofNullable(from.getSha()).map(sha -> "@" + sha).orElse("");
        } else if (instruction instanceof FromLocalImageBuild) {
            final FromLocalImageBuild fromLocalImageBuild = (FromLocalImageBuild) instruction;
            return "# " + fromLocalImageBuild.getOtherProjectPath() + "\n" +
                    "FROM " + fromLocalImageBuild.getTag().get();
        } else if (instruction instanceof Copy copySpec) {
            return "COPY " + Optional.ofNullable(copySpec.getOwner()).map(s -> "--chown=" + s + " ").orElse("") + copySpec.getLayer() + " /";
        } else if (instruction instanceof Run run) {
            String bindMounts = run.getBindMounts().stream()
                    .filter(m -> m.getSource().toFile().exists())
                    .map(m -> {
                        String opts = m.isReadWrite() ? "readwrite" : "readonly";
                        return "--mount=type=bind," + opts + ",target=" + m.getTarget() + ",source=" + workingDirectory.relativize(m.getSource());
                    }).collect(Collectors.joining(" "));
            return "RUN " + bindMounts + " " + String.join(" && \\ \n\t", run.getCommands());
        } else if (instruction instanceof CreateUser createUser) {
            return String.format(
                    "RUN if ! command -v busybox &> /dev/null; then \\ \n" +
                            "       groupadd -g %4$s %3$s ; \\ \n" +
                            "       useradd -r -s /bin/false -g %4$s --uid %2$s %1$s ; \\ \n" +
                            "   else \\ \n" + // Specific case for Alpine and Busybox
                            "       addgroup --gid %4$s %3$s ; \\ \n" +
                            "       adduser -S -s /bin/false --ingroup %3$s -H -D -u %2$s %1$s ; \\ \n" +
                            "   fi",
                    createUser.getUsername(),
                    createUser.getUserId(),
                    createUser.getGroup(),
                    createUser.getGroupId()
            );
        } else if (instruction instanceof SetUser) {
            return "USER " + ((SetUser) instruction).getUsername();
        } else if (instruction instanceof Install install) {
            return instructionAsDockerFileInstruction(
                    new Run(installCommands(install), installBindMounts(install, workingDirectory)),
                    workingDirectory);
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

    public List<String> repositoryFormat(Install.PackageInstaller installer, Install.Repository repository) {
        switch (installer) {
            case YUM:
                return Arrays.asList(
                        "[" + repository.getName() + "]",
                        "name=" + repository.getName(),
                        "baseurl=" + repository.getSecretUrl(),
                        "enabled=1",
                        "gpgcheck=0"
                );
            case APT:
                return Arrays.asList(
                        "deb " + repository.getSecretUrl()
                );
            default:
                throw new GradleException("Unexpected value for package installer generating command for " + installer);
        }
    }

    public List<Run.BindMount> installBindMounts(Install instruction, Path workingDirectory) {
        Path ephemeralPath = workingDirectory.resolve("ephemeral");
        Run.InternalBindMount ephemeralBindMount =
                new Run.InternalBindMount(ephemeralPath, EPHEMERAL_MOUNT, false);
        Install.PackageInstaller installer = instruction.getInstaller();

        if (!instruction.getRepositories().isEmpty() &&
                installer == Install.PackageInstaller.YUM) {
            return List.of(
                    ephemeralBindMount,
                    new Run.InternalBindMount(
                            ephemeralPath.resolve("repository").resolve("repos.d"),
                            "/etc/yum.repos.d", false)
            );
        } else if (!instruction.getRepositories().isEmpty() &&
                installer == Install.PackageInstaller.APT) {
            return List.of(
                    ephemeralBindMount,
                    new Run.InternalBindMount(
                            ephemeralPath.resolve("repository").resolve("repos.d"),
                            "/etc/apt/sources.list.d",
                            true)
            );
        } else {
            return List.of(ephemeralBindMount);
        }
    }

    public List<String> installCommands(Install instruction) {
        Install.PackageInstaller installer = instruction.getInstaller();
        List<Install.Package> packages = instruction.getPackages();
        List<Install.Repository> repositories = instruction.getRepositories();

        switch (installer) {
            case YUM:
                /*
                    yum install -y --disablerepo=* --enablerepo={repository} {package}-{version} {package}-{version} &&
                    yum clean all &&
                    rm -rf /var/cache/yum
                 */
                String enabledRepos = repositories.stream()
                        .map(Install.Repository::getName).collect(Collectors.joining(","));
                if (enabledRepos.isEmpty()) {
                    enabledRepos = "*";
                }
                String joinedRpmPackages = packages.stream()
                        .map(aPackage -> aPackage.getName() +
                                Optional.ofNullable(aPackage.getVersion()).map(v -> "-" + v).orElse(""))
                        .collect(Collectors.joining(" "));

                return Arrays.asList(
                        "yum install -y --disablerepo=* --enablerepo=" + enabledRepos + " " + joinedRpmPackages,
                        "yum clean all",
                        "rm -rf /var/cache/yum"
                );
            case APT:
                /*
                    mv /etc/apt/sources.list /etc/apt/sources.list.bak &&
                    sed -i -e 's=\$releasever='"$VERSION_CODENAME"'=' /etc/apt/sources.list.d/*.list &&
                    apt-get update &&
                    apt-get install -y {package}={version} {package}={version} &&
                    apt-get clean &&
                    rm -rf /var/lib/apt/lists/* /tmp/* /var/tmp/* &&
                    mv /etc/apt/sources.list.bak /etc/apt/sources.list
                 */
                String joinedAptPackages = packages.stream()
                        .map(aPackage -> aPackage.getName() +
                                Optional.ofNullable(aPackage.getVersion()).map(v -> "=" + v).orElse(""))
                        .collect(Collectors.joining(" "));

                return Stream.concat(
                        Stream.concat(
                                Stream.of(
                                        ". /etc/os-release",
                                        "mv /etc/apt/sources.list /etc/apt/sources.list.bak",
                                        "sed -i -e 's=\\$releasever='\"$VERSION_CODENAME\"'=' /etc/apt/sources.list.d/*.list"
                                ).filter(f -> !repositories.isEmpty()),
                                Stream.of(
                                        "export DEBIAN_FRONTEND=noninteractive",
                                        "apt-get -o Acquire::AllowInsecureRepositories=true -o Acquire::AllowDowngradeToInsecureRepositories=true update",
                                        "apt-get install -y --allow-unauthenticated " + joinedAptPackages,
                                        "apt-get clean",
                                        "rm -rf /var/lib/apt/lists/* /tmp/* /var/tmp/*"
                                )
                        ),
                        Stream.of("mv /etc/apt/sources.list.bak /etc/apt/sources.list").filter(f -> !repositories.isEmpty())
                ).collect(Collectors.toList());
            case APK:
                /* apk update &&
                    apk add {package}={version} &&
                    rm -rf /var/cache/apk/*
                */
                return Arrays.asList(
                        "apk update",
                        "apk add " + packages.stream().map(aPackage -> aPackage.getName() +
                                        Optional.ofNullable(aPackage.getVersion()).map(v -> "=" + v).orElse(""))
                                .collect(Collectors.joining(" ")),
                        "rm -rf /var/cache/apk/*");
            default:
                throw new GradleException("Unexpected value for package installer generating command for " + installer);
        }
    }

    @SuppressWarnings("unused")
    public UUID build(
            List<ContainerImageBuildInstruction> instructions,
            Path workingDirectory,
            Path imageArchive,
            Path idfile,
            Path createdAtFile
    ) throws IOException {
        if (!workingDirectory.toFile().isDirectory()) {
            throw new GradleException("Can't build docker image, missing working directory " + workingDirectory);
        }
        Path dockerFile = workingDirectory.resolve("Dockerfile");
        Files.writeString(
                dockerFile,
                dockerFileFromInstructions(
                        instructions,
                        workingDirectory
                )
        );
        Files.write(workingDirectory.resolve(".dockerignore"), ("**\n!" + CONTEXT_FOLDER + "\n!ephemeral").getBytes());

        final UUID uuid = UUID.randomUUID();

        // We build with --no-cache to make things more straight forward, since we already cache images using Gradle's build cache
        int imageBuild = dockerUtils.exec(spec -> {
            spec.setWorkingDir(dockerFile.getParent());
            if (System.getProperty("co.elastic.unsafe.use-docker-cache", "false").equals("true")) {
                // This is usefull for development when we don't care about image corectness, but otherwhise dagerous,
                //   e.g. dockerEphemeral content in run commands could lead to incorrect results
                spec.commandLine("docker", "image", "build", "--platform", "linux/" + Architecture.current().dockerName(), "--quiet=false", "--progress=plain", "--iidfile=" + idfile, ".", "-t", uuid);
            } else {
                spec.commandLine("docker", "image", "build",  "--platform", "linux/" + Architecture.current().dockerName(), "--quiet=false", "--no-cache", "--progress=plain", "--iidfile=" + idfile, ".", "-t", uuid);
            }
            spec.setIgnoreExitValue(true);
        }).getExitValue();
        if (imageBuild != 0) {
            throw new GradleException("Failed to build docker image, see the docker build log in the task output. " +
                    "If a package can't be found, reach out to #cloud-delivery as it might have to be mirrored in Artifactory.");
        }

        try (BufferedOutputStream createAtFileOut = new BufferedOutputStream(Files.newOutputStream(createdAtFile))) {
            int imageInspect = dockerUtils.exec(spec -> {
                spec.setWorkingDir(dockerFile.getParent());
                spec.setStandardOutput(createAtFileOut);
                spec.commandLine("docker", "image", "inspect", "--format", "{{.Created}}", uuid);
                spec.setIgnoreExitValue(true);
            }).getExitValue();
            if (imageInspect != 0) {
                throw new GradleException("Failed to inspect docker image, see the docker build log in the task output");
            }
        }

        // We build in daemon only to export, we could use docker buildx  to create a tar directly, but unfrotunetly this
        // prooved buggy, e.g. the --iidfile was different from the ID the image would have when imported into the daemon
        saveCompressedDockerImage(uuid.toString(), imageArchive);
        return uuid;
    }

    private void saveCompressedDockerImage(String imageId, Path imageArchive) throws IOException {
        try (ZstdCompressorOutputStream compressedOut = new ZstdCompressorOutputStream(
                new BufferedOutputStream(Files.newOutputStream(imageArchive)))) {
            ExecResult imageSave = dockerUtils.exec(spec -> {
                spec.setStandardOutput(compressedOut);
                spec.setCommandLine("docker", "save", imageId);
                spec.setIgnoreExitValue(true);
            });
            if (imageSave.getExitValue() != 0) {
                throw new GradleException("Failed to save docker image, see the docker build log in the task output");
            }
        }
    }
}
