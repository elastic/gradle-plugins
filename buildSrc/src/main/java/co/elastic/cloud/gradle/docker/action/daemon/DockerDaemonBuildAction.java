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

package co.elastic.cloud.gradle.docker.action.daemon;

import co.elastic.cloud.gradle.docker.DockerFileExtension;
import co.elastic.cloud.gradle.docker.Package;
import co.elastic.cloud.gradle.docker.build.DockerBuildInfo;
import co.elastic.cloud.gradle.docker.build.DockerImageExtension;
import com.google.gson.Gson;
import org.gradle.api.GradleException;
import org.gradle.api.Project;
import org.gradle.process.ExecOperations;
import org.gradle.process.ExecResult;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;
import java.util.stream.Collectors;

public class DockerDaemonBuildAction {

    private final DockerFileExtension extension;
    private final ExecOperations execOperations;
    private final Project project;

    public DockerDaemonBuildAction(DockerFileExtension extension, ExecOperations execOperations, Project project) {
        this.extension = extension;
        this.execOperations = execOperations;
        this.project = project;
    }

    public File getDockerSave() {
        return getExtension().getContext().projectTarImagePath().toFile();
    }

    public Path getImageBuildInfo() {
        return getExtension().getContext().imageBuildInfo();
    }

    public DockerBuildInfo execute(String tag) throws IOException {
        File workingDir = getExtension().getContext().contextPath().toFile();
        if (!workingDir.isDirectory()) {
            throw new GradleException("Can't build docker image, missing working directory " + workingDir);
        }
        Path dockerfile = workingDir.toPath().getParent().resolve("Dockerfile");
        generateDockerFile(dockerfile);

        // Create a dockerignore file
        Files.write(workingDir.toPath().getParent().resolve(".dockerignore"), "**\n!context".getBytes());

        ExecOperations execOperations = getExecOperations();

        ExecResult imageBuild = execOperations.exec(spec -> {
            spec.setWorkingDir(dockerfile.getParent());
            // Remain independent from the host environment
            spec.setEnvironment(Collections.emptyMap());
            // We build with --no-cache to make things more straight forward, since we already cache images using Gradle's build cache
            spec.setCommandLine(
                    "docker", "image", "build", "--no-cache", "--tag=" + tag, "."
            );
            spec.setIgnoreExitValue(true);
        });
        if (imageBuild.getExitValue() != 0) {
            throw new GradleException("Failed to build docker image, see the docker build log in the task output");
        }
        ExecResult imageSave = execOperations.exec(spec -> {
            spec.setWorkingDir(getDockerSave().getParent());
            spec.setEnvironment(Collections.emptyMap());
            spec.setCommandLine("docker", "save", "--output=" + getDockerSave().getName(), tag);
            spec.setIgnoreExitValue(true);
        });
        if (imageSave.getExitValue() != 0) {
            throw new GradleException("Failed to save docker image, see the docker build log in the task output");
        }

        // Load imageId
        ByteArrayOutputStream commandOutput = new ByteArrayOutputStream();
        execOperations.exec(spec -> {
            spec.setStandardOutput(commandOutput);
            spec.setEnvironment(Collections.emptyMap());
            spec.commandLine("docker", "inspect", "--format='{{index .Id}}'", tag);
        });
        String imageId = new String(commandOutput.toByteArray(), StandardCharsets.UTF_8).trim().replaceAll("'", "");

        getProject().getLogger().info("Built image {} sha256:{}", tag, imageId);

        return new DockerBuildInfo()
                .setTag(tag)
                .setBuilder(DockerBuildInfo.Builder.DAEMON)
                .setImageId(imageId);
    }

    private void generateDockerFile(Path targetFile) throws IOException {
        if (Files.exists(targetFile)) {
            Files.delete(targetFile);
        }
        try (BufferedWriter writer = Files.newBufferedWriter(targetFile)) {
            writer.write("#############################\n");
            writer.write("#                           #\n");
            writer.write("# Auto generated Dockerfile #\n");
            writer.write("#                           #\n");
            writer.write("#############################\n\n");

            DockerFileExtension extension = getExtension();
            writer.write(
                    extension.getFromProject().map(otherProject -> {
                        String otherProjectImageReference = otherProject.getExtensions()
                                .getByType(DockerImageExtension.class)
                                .getBuildInfo()
                                .getTag();
                        return "# " + otherProject.getPath() + " (a.k.a " + otherProjectImageReference + ")\n" +
                                "FROM " + otherProjectImageReference + "\n\n";
                    }).orElse("FROM " + getExtension().getFrom() + "\n\n"));

            if (getExtension().getMaintainer() != null) {
                writer.write("LABEL maintainer=" + getExtension().getMaintainer() + "\n\n");
            }

            if (getExtension().getUser() != null) {
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

            if (!extension.getPackages().isEmpty()) {
                writer.write("# Packages installation\n");
                writer.write("RUN " + extension.getPackages()
                        .entrySet()
                        .stream()
                        .flatMap(entry -> installCommands(entry.getKey(), entry.getValue()).stream())
                        .collect(Collectors.joining("&& \\ \n \t")) + "\n");
                writer.newLine();
            }

            writer.write("# FS hierarchy is set up in Gradle, so we just copy it in\n");
            writer.write("# COPY and RUN commands are kept consistent with the DSL\n");

            getExtension().forEachCopyAndRunLayer(
                    (ordinal, commands) -> {
                        try {
                            writer.write("RUN " + String.join(" && \\\n    ", commands) + "\n");
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

            if (!getExtension().getEntryPoint().isEmpty()) {
                writer.write("ENTRYPOINT " + getExtension().getEntryPoint().stream().map(x -> "\"" + x + "\"").collect(Collectors.joining(", ")) + "\n\n");
            }
            if (!getExtension().getCmd().isEmpty()) {
                writer.write("CMD " + getExtension().getCmd() + "\n\n");
            }

            for (Map.Entry<String, String> entry : getExtension().getLabels().entrySet()) {
                writer.write("LABEL " + entry.getKey() + "=" + entry.getValue() + "\n");
            }
            if (!getExtension().getLabels().isEmpty()) {
                writer.write("\n");
            }

            for (Map.Entry<String, String> entry : getExtension().getEnv().entrySet()) {
                writer.write("ENV " + entry.getKey() + "=" + entry.getValue() + "\n");
            }
            if (!getExtension().getEnv().isEmpty()) {
                writer.write("\n");
            }

        }
    }

    public List<String> installCommands(Package.PackageInstaller installer, List<Package> packages) {
        List<String> result = new ArrayList<>();
        switch (installer) {
            case YUM:
                /*
                    yum install -y {package}-{version} {package}-{version} &&
                    yum clean all &&
                    rm -rf /var/cache/yum
                 */
                result.addAll(Arrays.asList(
                        "yum install -y " + packages.stream().map(aPackage -> aPackage.getName() + "-" + aPackage.getVersion()).collect(Collectors.joining(" ")),
                        "yum clean all",
                        "rm -rf /var/cache/yum"
                ));
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

    public ExecOperations getExecOperations() {
        return execOperations;
    }

    public DockerFileExtension getExtension() {
        return extension;
    }

    public Project getProject() {
        return project;
    }
}
