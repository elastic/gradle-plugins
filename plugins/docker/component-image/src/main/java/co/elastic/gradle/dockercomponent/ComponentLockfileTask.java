package co.elastic.gradle.dockercomponent;

import co.elastic.gradle.cli.manifest.ManifestToolExecTask;
import co.elastic.gradle.dockercomponent.lockfile.ComponentLockfile;
import co.elastic.gradle.utils.Architecture;
import co.elastic.gradle.utils.RegularFileUtils;
import co.elastic.gradle.utils.docker.UnchangingContainerReference;
import co.elastic.gradle.utils.docker.instruction.ContainerImageBuildInstruction;
import co.elastic.gradle.utils.docker.instruction.From;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.gradle.api.GradleException;
import org.gradle.api.file.RegularFileProperty;
import org.gradle.api.provider.MapProperty;
import org.gradle.api.provider.Provider;
import org.gradle.api.tasks.*;
import org.gradle.process.ExecOperations;
import org.gradle.process.ExecResult;
import org.jetbrains.annotations.NotNull;

import javax.inject.Inject;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.time.Instant;
import java.util.*;
import java.util.stream.Collectors;

public abstract class ComponentLockfileTask extends ManifestToolExecTask {

    public ComponentLockfileTask() {
        setArgs(List.of("--version"));
    }

    @Nested
    public abstract MapProperty<Architecture, List<ContainerImageBuildInstruction>> getInstructions();

    @Input
    public Instant getCurrentTime() {
        // Task should never be considered up-to-date
        return Instant.now();
    }

    @OutputFile
    public abstract RegularFileProperty getLockFileLocation();

    @Inject
    protected abstract ExecOperations getExecOperations();

    @TaskAction
    public void generateLockFile() throws IOException {
        if (getInstructions().get().values().stream()
                .flatMap(Collection::stream)
                .noneMatch(each -> each instanceof From)) {
            throw new StopExecutionException("No static images in image definition");
        }


        final String reference = getDockerReferenceFromInstructions();
        // TODO: latest versions of jib-core seems to have the infrastructure to pull and push manifests, we could use that instead of the manifest-tool
        final String output = getManifestToolInspectRawJason(reference);

        getLogger().info("Reading manifest list with manifest-tool --raw:\n{}", output);

        final JsonNode root = new ObjectMapper().readTree(output);
        final Map<Architecture, String> result = new HashMap<>();
        final String digest = root.get("digest").asText();
        if (digest == null || digest.equals("")) {
            throw new GradleException("manifest-tool did not return the expected digest");
        }
        for (Architecture arch : Architecture.values()) {
            result.put(arch, digest);
        }

        try (Writer writer = Files.newBufferedWriter(RegularFileUtils.toPath(getLockFileLocation()))) {
            ComponentLockfile.write(
                    new ComponentLockfile(
                            result.entrySet().stream()
                                    .collect(Collectors.toMap(
                                            Map.Entry::getKey,
                                            entry -> {
                                                final String[] splitRef = reference.split(":", 2);
                                                return new UnchangingContainerReference(
                                                        splitRef[0],
                                                        splitRef[1],
                                                        entry.getValue()
                                                );
                                            }
                                    ))
                    ),
                    writer
            );
        }
    }

    private String getDockerReferenceFromInstructions() {
        final Set<String> allTags = getInstructions().get().values().stream()
                .flatMap(Collection::stream)
                .filter(each -> each instanceof From)
                .map(each -> (From) each)
                .map(From::getReference)
                .map(Provider::get)
                .peek(each -> {
                    if (each.contains("@")) {
                        throw new IllegalStateException("Did not expect to have a digest for: " + each);
                    }
                })
                .collect(Collectors.toSet());

        if (allTags.size() != 1) {
            throw new GradleException("Expected the 'from' image to be a single tag for all platforms " +
                                      "pointing to a manifest list, but found: " + allTags);
        }
        final String tag = allTags.iterator().next();
        return tag;
    }

    @NotNull
    private String getManifestToolInspectRawJason(String reference) {
        final String output;
        try {
            final ExecResult result;
            try (ByteArrayOutputStream out = new ByteArrayOutputStream()) {
                Map<String, String> env = new HashMap<>();
                env.put("PATH", System.getenv("PATH"));
                env.put("HOME", System.getProperty("user.home"));

                result = getExecOperations().exec(spec -> {
                    spec.setEnvironment(env);
                    spec.setExecutable(getExecutable());

                    spec.setArgs(Arrays.asList(
                            "inspect", "--raw", reference
                    ));
                    spec.setStandardOutput(out);
                    spec.setIgnoreExitValue(true);
                });
                output = out.toString(StandardCharsets.UTF_8).trim();
            }
            if (result.getExitValue() != 0) {
                throw new GradleException("Reading the manifest list failed: " + output);
            }
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
        return output;
    }

}
