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

package co.elastic.cloud.gradle.docker.action;

import co.elastic.cloud.gradle.docker.DockerFileExtension;
import co.elastic.cloud.gradle.docker.Package;
import co.elastic.cloud.gradle.docker.build.DockerBuildInfo;
import co.elastic.cloud.gradle.docker.build.DockerImageExtension;
import com.google.cloud.tools.jib.tar.TarExtractor;
import org.gradle.api.Action;
import org.gradle.api.GradleException;
import org.gradle.process.ExecOperations;
import org.gradle.process.ExecResult;
import org.gradle.process.ExecSpec;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.*;
import java.util.stream.Collectors;
import java.util.stream.Stream;

public class DockerDaemonActions {

    private final ExecOperations execOperations;

    public DockerDaemonActions(ExecOperations execOperations) {
        this.execOperations = execOperations;
    }

    public void loadArchive(Path archive) {
        try (InputStream archiveInput = TarExtractor.getSpecificInputStream(new BufferedInputStream(Files.newInputStream(archive)))) {
            execute(spec -> {
                spec.setEnvironment(Collections.emptyMap());
                spec.setStandardInput(archiveInput);
                spec.commandLine("docker", "load");
            });
        } catch (IOException e) {
            throw new GradleException("Error importing image in docker daemon", e);
        }
    }

    public String clean(String tag) throws IOException {
        try(ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            execute(spec -> {
                spec.setEnvironment(Collections.emptyMap());
                spec.commandLine("docker", "image", "rm", tag);
                spec.setIgnoreExitValue(true);
                spec.setErrorOutput(out);
            });
            return out.toString(StandardCharsets.UTF_8.name());
        }
    }

    public void pull(String from) {
        execute(spec -> {
            spec.setEnvironment(Collections.emptyMap());
            spec.commandLine("docker", "--config", System.getProperty("user.home") + File.separator + ".docker", "pull", from);
        });
    }

    public DockerBuildInfo build(DockerFileExtension extension, String tag) throws IOException {
        File workingDir = extension.getContext().basePath().toFile();
        if (!workingDir.isDirectory()) {
            throw new GradleException("Can't build docker image, missing working directory " + workingDir);
        }
        Path dockerfile = workingDir.toPath().resolve("Dockerfile");
        generateDockerFile(extension, dockerfile);

        Files.write(workingDir.toPath().resolve(".dockerignore"), "**\n!context\n!dockerEphemeral".getBytes());

        // We build with --no-cache to make things more straight forward, since we already cache images using Gradle's build cache

        int imageBuild = execute(spec -> {
            spec.setWorkingDir(dockerfile.getParent());
            spec.commandLine("docker", "image", "build", "--quiet=false", "--no-cache", "--progress=plain", "--tag=" + tag, ".");
            spec.setIgnoreExitValue(true);
        }).getExitValue();
        if (imageBuild != 0) {
            throw new GradleException("Failed to build docker image, see the docker build log in the task output");
        }

        // Load imageId

        ByteArrayOutputStream commandOutput = new ByteArrayOutputStream();
        execute(spec -> {
            spec.setStandardOutput(commandOutput);
            spec.setEnvironment(Collections.emptyMap());
            spec.commandLine("docker", "inspect", "--format='{{index .Id}}'", tag);
        });
        String imageId = new String(commandOutput.toByteArray(), StandardCharsets.UTF_8).trim().replaceAll("'", "");

        return new DockerBuildInfo()
                .setTag(tag)
                .setBuilder(DockerBuildInfo.Builder.DAEMON)
                .setImageId(imageId)
                .setCreatedAt(Instant.now());
    }

    private void generateDockerFile(DockerFileExtension extension, Path targetFile) throws IOException {
        if (Files.exists(targetFile)) {
            Files.delete(targetFile);
        }
        try (BufferedWriter writer = Files.newBufferedWriter(targetFile)) {
            writer.write("#############################\n");
            writer.write("#                           #\n");
            writer.write("# Auto generated Dockerfile #\n");
            writer.write("#                           #\n");
            writer.write("#############################\n\n");
            writer.write("# syntax = docker/dockerfile:experimental\n\n");

            writer.write(
                    extension.getFromProject().map(otherProject -> {
                        String otherProjectImageReference = otherProject.getExtensions()
                                .getByType(DockerImageExtension.class)
                                .getBuildInfo()
                                .getTag();
                        return "# " + otherProject.getPath() + " (a.k.a " + otherProjectImageReference + ")\n" +
                                "FROM " + otherProjectImageReference + "\n\n";
                    }).orElse("FROM " + extension.getFrom() + "\n\n"));

            if (extension.getMaintainer() != null) {
                writer.write("LABEL maintainer=\"" + extension.getMaintainer() + "\"\n\n");
            }

            if (extension.getUser() != null) {
                writer.write(String.format(
                        "RUN if ! command -v busybox &> /dev/null; then \\ \n" +
                                "       groupadd -g %4$s %3$s ; \\ \n" +
                                "       useradd -r -s /bin/false -g %4$s --uid %2$s %1$s ; \\ \n" +
                                "   else \\ \n" + // Specific case for Alpine and Busybox
                                "       addgroup --gid %4$s %3$s ; \\ \n" +
                                "       adduser -S -s /bin/false --ingroup %3$s -H -D -u %2$s %1$s ; \\ \n" +
                                "   fi \n\n",
                        extension.getUser().user,
                        extension.getUser().uid,
                        extension.getUser().group,
                        extension.getUser().gid
                ));
            }

            String mountDependencies = "--mount=type=bind,target=" + extension.getDockerEphemeral() + ",source=dockerEphemeral ";

            if (!extension.getPackages().isEmpty()) {
                writer.write("# Packages installation\n");
                writer.write("RUN " + mountDependencies + extension.getPackages()
                        .entrySet()
                        .stream()
                        .flatMap(entry -> installCommands(entry.getKey(), entry.getValue()).stream())
                        .collect(Collectors.joining(" && \\ \n \t")) + "\n");
                writer.newLine();
            }

            writer.write(extension.getEnv().entrySet().stream()
                    .map(entry -> "ENV " + entry.getKey() + "=" + entry.getValue() + "\n")
                    .reduce("" , String::concat));

            writer.write("# FS hierarchy is set up in Gradle, so we just copy it in\n");
            writer.write("# COPY and RUN commands are kept consistent with the DSL\n");

            extension.forEachCopyAndRunLayer(
                    (ordinal, commands) -> {
                        try {
                            writer.write("RUN " + mountDependencies + String.join(" && \\\n    ", commands) + "\n");
                        } catch (IOException e) {
                            throw new UncheckedIOException(e);
                        }
                    },
                    (ordinal, copySpecAction) -> {
                        try {
                            writer.write("COPY " + copySpecAction.owner.map(s -> "--chown=" + s + " ").orElse("") + extension.getContext().contextPath().toFile().getName() + "/" + "layer" + ordinal + " /\n");
                        } catch (IOException e) {
                            throw new UncheckedIOException(e);
                        }
                    }
            );
            writer.write("\n");

            if (extension.getRunUser() != null && !extension.getRunUser().isEmpty()) {
                writer.write("USER "+ extension.getRunUser() +"\n");
            }

            if (!extension.getEntryPoint().isEmpty()) {
                writer.write("ENTRYPOINT [" + extension.getEntryPoint().stream().map(x -> "\"" + x + "\"").collect(Collectors.joining(", ")) + "]\n\n");
            }
            if (!extension.getCmd().isEmpty()) {
                writer.write("CMD [" + extension.getCmd().stream().map(x -> "\"" + x + "\"").collect(Collectors.joining(", ")) + "]\n\n");
            }

            if (extension.getHealthcheck() != null) {
                writer.write("HEALTHCHECK " + healthcheckInstruction(extension.getHealthcheck()) + "\n");
            }

            for (Map.Entry<String, String> entry : extension.getLabels().entrySet()) {
                writer.write("LABEL " + entry.getKey() + "=" + entry.getValue() + "\n");
            }
            if (!extension.getLabels().isEmpty()) {
                writer.write("\n");
            }

            if(extension.getWorkDir() != null) {
                writer.write("WORKDIR " + extension.getWorkDir() + "\n");
            }

            if(!extension.getExposeTcp().isEmpty() ||
                    !extension.getExposeUdp().isEmpty()) {
                writer.write(
                        "EXPOSE " + Stream.concat(
                                extension.getExposeTcp().stream()
                                        .map(each -> each + "/tcp"),
                                extension.getExposeUdp().stream()
                                        .map(each -> each + "/udp")
                        )
                                .collect(Collectors.joining(" ")) +
                                "\n"
                );
            }
        }
    }

    private String healthcheckInstruction(DockerFileExtension.Healthcheck healthcheck) {
        return Optional.ofNullable(healthcheck.getInterval()).map(interval -> "--interval=" + interval + " ").orElse("") +
                Optional.ofNullable(healthcheck.getTimeout()).map(timeout -> "--timeout=" + timeout + " ").orElse("") +
                Optional.ofNullable(healthcheck.getStartPeriod()).map(startPeriod -> "--start-period=" + startPeriod + " ").orElse("") +
                Optional.ofNullable(healthcheck.getRetries()).map(retries -> "--retries=" + retries + " ").orElse("") +
                "CMD " + healthcheck.getCmd();
    }

    public List<String> installCommands(Package.PackageInstaller installer, List<Package> packages) {
        List<String> result = new ArrayList<>();
        switch (installer) {
            // temporary workaround (https://github.com/elastic/cloud/issues/70170)
            case CONFIG:
                result.addAll(packages.stream().map(aPackage -> aPackage.getName()).collect(Collectors.toList()));
                break;
            case YUM:
                /*
                    yum update -y &&
                    yum install -y {package}-{version} {package}-{version} &&
                    yum clean all &&
                    rm -rf /var/cache/yum
                 */
                result.addAll(Arrays.asList(
                        "yum install -y " + packages.stream().map(aPackage -> aPackage.getName() + "-" + aPackage.getVersion()).collect(Collectors.joining(" ")),
                        "yum clean all",
                        "rm -rf /var/cache/yum"
                ));
                break;
            case APT:
                /*
                    apt-get update &&
                    apt-get install -y {package}={version} {package}={version} &&
                    apt-get clean &&
                    rm -rf /var/lib/apt/lists/* /tmp/* /var/tmp/*
                 */
                result.addAll(Arrays.asList(
                        "apt-get update",
                        "apt-get install -y " + packages.stream().map(aPackage -> "" + aPackage.getName() + "=" + aPackage.getVersion()).collect(Collectors.joining(" ")),
                        "apt-get clean",
                        "rm -rf /var/lib/apt/lists/* /tmp/* /var/tmp/*"));
                break;
            case APK:
                /* apk update &&
                    apk add {package}={version} &&
                    rm -rf /var/cache/apk/*
                */
                result.addAll(Arrays.asList(
                        "apk update",
                        "apk add " + packages.stream().map(aPackage -> aPackage.getName() + "=" + aPackage.getVersion()).collect(Collectors.joining(" ")),
                        "rm -rf /var/cache/apk/*"));
                break;
            default:
                throw new GradleException("Unexpected value for package installer generating command for " + installer);
        }
        return result;
    }

    public ExecResult execute(Action<? super ExecSpec> action) {
        Map<String,String> environment = new HashMap<>();
        // Only pass specific env vars for more reproducible builds
        environment.put("LANG", System.getenv("LANG"));
        environment.put("LC_ALL", System.getenv("LC_ALL"));
        environment.put("DOCKER_BUILDKIT", "1");
        return execOperations.exec(spec -> {
            action.execute(spec);
            spec.setEnvironment(environment);
        });
    }

    public void checkVersion() throws IOException {
        ByteArrayOutputStream commandOutput = new ByteArrayOutputStream();
        execute(spec -> {
            spec.setStandardOutput(commandOutput);
            spec.setEnvironment(Collections.emptyMap());
            spec.commandLine("docker", "version", "--format='{{.Server.Version}}'");
        });
        String dockerVersion = new String(commandOutput.toByteArray(), StandardCharsets.UTF_8).trim().replaceAll("'", "");;
        int dockerMajorVersion = Integer.parseInt(dockerVersion.split("\\.")[0]);
        if (dockerMajorVersion < 19) {
            throw new IllegalStateException("Docker daemon version must be 19 and above. Currently " + dockerVersion);
        }
    }
}
