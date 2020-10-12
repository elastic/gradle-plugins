package co.elastic.cloud.gradle.docker.daemon;

import co.elastic.cloud.gradle.docker.DockerFileExtension;
import co.elastic.cloud.gradle.docker.DockerPluginConventions;
import co.elastic.cloud.gradle.docker.build.DockerBuildBaseTask;
import co.elastic.cloud.gradle.docker.build.DockerBuildInfo;
import co.elastic.cloud.gradle.docker.build.DockerBuildResultExtension;
import co.elastic.cloud.gradle.docker.build.DockerImageExtension;
import com.google.cloud.tools.jib.api.ImageReference;
import com.google.gson.Gson;
import org.gradle.api.GradleException;
import org.gradle.api.tasks.CacheableTask;
import org.gradle.api.tasks.OutputFile;
import org.gradle.api.tasks.TaskAction;
import org.gradle.process.ExecOperations;
import org.gradle.process.ExecResult;

import javax.inject.Inject;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Collections;
import java.util.Map;
import java.util.stream.Collectors;

@CacheableTask
public class DockerDaemonBuildTask extends DockerBuildBaseTask {

    @OutputFile
    public File getDockerSave() {
        return getExtension().getContext().projectTarImagePath().toFile();
    }

    @OutputFile
    public Path getImageBuildInfo() {
        return getExtension().getContext().imageBuildInfo();
    }

    @TaskAction
    public void doBuildDockerImage() throws IOException {
        File workingDir = getExtension().getContext().contextPath().toFile();
        if (!workingDir.isDirectory()) {
            throw new GradleException("Can't build docker image, missing working directory " + workingDir);
        }
        Path dockerfile = workingDir.toPath().getParent().resolve("Dockerfile");
        generateDockerFile(dockerfile);

        // Create a dockerignore file
        Files.write(workingDir.toPath().getParent().resolve(".dockerignore"), "**\n!context".getBytes());

        ExecOperations execOperations = getExecOperations();

        String tag = DockerPluginConventions.imageReference(getProject()).toString();
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
        ImageReference imageReference = DockerPluginConventions.imageReference(getProject(), imageId);
        getLogger().info("Built image {}", imageReference);

        getProject().getExtensions().add(DockerBuildResultExtension.class,
                "dockerBuildResult",
                new DockerBuildResultExtension(imageId, getDockerSave().toPath()));

        try (FileWriter writer = new FileWriter(getExtension().getContext().imageBuildInfo().toFile())) {
            writer.write(new Gson().toJson(new DockerBuildInfo()
                    .setTag(tag)
                    .setBuilder(DockerBuildInfo.Builder.DAEMON)
                    .setImageId(imageId)));
        } catch (IOException e) {
            throw new GradleException("Error writing image info file", e);
        }
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
                                .getContext()
                                .loadBuildInfo()
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
                            String chown = copySpecAction.owner.isPresent() ?
                                    "--chown=" + copySpecAction.owner.get() + " " :
                                    "";
                            writer.write("COPY " + chown + extension.getContext().contextPath().toFile().getName() + "/" + "layer" + ordinal + " /\n");
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

            for (Map.Entry<String, String> entry : getExtension().getLabel().entrySet()) {
                writer.write("LABEL " + entry.getKey() + "=" + entry.getValue() + "\n");
            }
            if (!getExtension().getLabel().isEmpty()) {
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

    @Inject
    public ExecOperations getExecOperations() {
        throw new IllegalStateException("not implemented");
    }
}
