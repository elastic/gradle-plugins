package co.elastic.gradle.dockerbase;


import co.elastic.gradle.utils.Architecture;
import co.elastic.gradle.utils.docker.DockerUtils;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.apache.commons.csv.CSVFormat;
import org.apache.commons.csv.CSVParser;
import org.gradle.api.DefaultTask;
import org.gradle.api.GradleException;
import org.gradle.api.file.ProjectLayout;
import org.gradle.api.file.RegularFileProperty;
import org.gradle.api.provider.Property;
import org.gradle.api.tasks.*;
import org.gradle.process.ExecOperations;

import javax.inject.Inject;
import java.io.*;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@CacheableTask
abstract public class DockerLockfileTask extends DefaultTask {

    public static final String LOCKFILE_NAME = "docker-base-image.lock";
    private static final String PRINT_INSTALLED_PACKAGES_NAME = "print-installed-packages.sh";

    public DockerLockfileTask() {
        getLockFile().convention(
                getProjectLayout().getProjectDirectory().file(LOCKFILE_NAME)
        );
    }

    @Inject
    abstract protected ProjectLayout getProjectLayout();

    @Inject
    abstract public ExecOperations getExecOperations();

    @Input
    public Architecture getArchitecture() {
        return Architecture.current();
    }

    @Input
    abstract public Property<String> getTag();

    @Input
    @Optional
    abstract public Property<Lockfile.Image> getLockfileImage();

    @OutputFile
    abstract public RegularFileProperty getLockFile();

    @TaskAction
    public void generateLockfile() {
        DockerUtils daemonActions = new DockerUtils(getExecOperations());
        try (ByteArrayOutputStream stdout = new ByteArrayOutputStream()) {
            copyResources();
            daemonActions.exec(spec -> {
                spec.setStandardOutput(stdout);
                spec.commandLine("docker", "run", "--rm", "-v",
                        String.format("%s:/buildDir", getBuildDir()),
                        getTag().get(),
                        "bash",
                        "/buildDir/" + PRINT_INSTALLED_PACKAGES_NAME);
            });

            try (InputStreamReader reader = new InputStreamReader(new ByteArrayInputStream(stdout.toByteArray()))) {
                CSVParser parser = CSVParser.parse(reader, CSVFormat.DEFAULT);
                List<Lockfile.LockedPackage> packages = parser.getRecords().stream().map(record -> {
                    String name = record.get(0);
                    String version = record.get(1);
                    String release = record.get(2);
                    String arch = record.get(3);
                    return new Lockfile.LockedPackage(name, version, release, arch);
                }).collect(Collectors.toList());

                Lockfile.Image image = getLockfileImage().getOrNull();
                if (image != null && image.getDigest() == null) {
                    String digest = getManifestDigest(image);
                    if (digest == null) {
                        throw new GradleException(
                                String.format(
                                        "Could not get the manifest digest for image [%s:%s], architecture [%s]",
                                        image.getRepository(),
                                        image.getTag(),
                                        getArchitecture().dockerName()));
                    }
                    image = new Lockfile.Image(
                            image.getRepository(),
                            image.getTag(),
                            digest
                    );
                }
                File file = getLockFile().get().getAsFile();
                Lockfile lockfile;
                if (file.exists()) {
                    lockfile = Lockfile.parse(Files.newBufferedReader(file.toPath()));
                } else {
                    lockfile = new Lockfile(new HashMap<>());
                }
                try (Writer writer = new FileWriter(file)) {
                    Map<String, Lockfile.Architecture> architectures = lockfile.getArchitectures();
                    architectures.put(getArchitecture().dockerName(), new Lockfile.Architecture(image, packages));
                    Lockfile.write(new Lockfile(architectures), writer);
                }
            }
        } catch (IOException e) {
            throw new UncheckedIOException("Failed to generate the package lockfile", e);
        }
    }

    public String getManifestDigest(Lockfile.Image image) {
        DockerUtils daemonActions = new DockerUtils(getExecOperations());
        try (ByteArrayOutputStream stdout = new ByteArrayOutputStream()) {
            daemonActions.exec(spec -> {
                spec.setStandardOutput(stdout);
                spec.commandLine("docker", "manifest", "inspect",
                        String.format("%s:%s", image.getRepository(), image.getTag()));
            });
            try (InputStreamReader reader = new InputStreamReader(new ByteArrayInputStream(stdout.toByteArray()))) {
                ObjectMapper mapper = new ObjectMapper();
                JsonNode root = mapper.readTree(reader);
                String digest = null;
                Iterator<JsonNode> manifests = root.path("manifests").elements();
                while (manifests.hasNext()) {
                    JsonNode manifest = manifests.next();
                    if (getArchitecture().dockerName().equals(manifest.path("platform").path("architecture").asText())) {
                        digest = manifest.path("digest").asText(null);
                        break;
                    }
                }
                return digest;
            }
        } catch (IOException e) {
            throw new UncheckedIOException("Failed to read the image manifest", e);
        }
    }

    private Path getBuildDir() {
        return getProjectLayout().getBuildDirectory().get().dir(getName()).getAsFile().toPath();
    }

    private void copyResources() throws IOException {
        InputStream resourceStream = getClass().getResourceAsStream(String.format("/%s", PRINT_INSTALLED_PACKAGES_NAME));
        if (resourceStream == null) {
            throw new GradleException(
                    String.format("Could not find an embedded resource for %s", PRINT_INSTALLED_PACKAGES_NAME));
        }
        Files.createDirectories(getBuildDir());
        Files.copy(resourceStream, getBuildDir().resolve(PRINT_INSTALLED_PACKAGES_NAME), StandardCopyOption.REPLACE_EXISTING);
    }
}
