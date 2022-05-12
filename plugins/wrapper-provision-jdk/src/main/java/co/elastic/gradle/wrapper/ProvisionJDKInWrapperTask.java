package co.elastic.gradle.wrapper;

import co.elastic.gradle.utils.Architecture;
import co.elastic.gradle.utils.OS;
import org.gradle.api.DefaultTask;
import org.gradle.api.GradleException;
import org.gradle.api.provider.MapProperty;
import org.gradle.api.provider.Property;
import org.gradle.api.tasks.Input;
import org.gradle.api.tasks.Optional;
import org.gradle.api.tasks.TaskAction;

import java.io.*;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import java.util.stream.Stream;

abstract public class ProvisionJDKInWrapperTask extends DefaultTask {

    public ProvisionJDKInWrapperTask() {
        getJdkCacheDir().convention("$HOME/.gradle/jdks");
    }

    @Input
    abstract public Property<String> getJavaReleaseName();

    @Input
    abstract public Property<String> getJdkCacheDir();

    @Input
    abstract public MapProperty<OS, Map<Architecture, String>> getChecksums();

    @Input
    @Optional
    abstract public Property<String> getAppleM1URLOverride();

    @TaskAction
    public void extendWrapper() throws IOException {
        final Map<OS, Map<Architecture, String>> checksums = getChecksums().get();
        final HashSet<OS> missingOS = new HashSet<>(List.of(OS.values()));
        missingOS.removeAll(checksums.keySet());
        if (!missingOS.isEmpty()) {
            throw new GradleException("A checksum for the following OSes missing:" + missingOS);
        }
        for (OS os : OS.values()) {
            final HashSet<Architecture> missingArch = new HashSet<>(List.of(Architecture.values()));
            missingArch.removeAll(
                    checksums.get(os).keySet()
            );
            if (!missingArch.isEmpty()) {
                throw new GradleException("Missing checksum for architecture on " + os + " : " + missingArch);
            }
        }
        final Path gradlewPath = getProject().getRootDir().toPath().resolve("gradlew");
        final Path gradlewNewPath = getProject().getRootDir().toPath().resolve("gradlew.new");
        try (
                final Stream<String> gradlew = Files.lines(gradlewPath)
        ) {
            Files.write(
                    gradlewNewPath,
                    gradlew.map(line -> {
                        if (line.startsWith("CLASSPATH=")) {
                            final String downloadUrl = "https://api.adoptium.net/v3/binary/version/" +
                                                       String.format(
                                                               "%s/%s/%s/jdk/hotspot/normal/eclipse?project=jdk",
                                                               URLEncoder.encode(getJavaReleaseName().get(), StandardCharsets.UTF_8),
                                                               OS.current().map(Map.of(
                                                                       OS.LINUX, "linux",
                                                                       OS.DARWIN, "mac"
                                                               )),
                                                               Architecture.current().map(Map.of(
                                                                       Architecture.X86_64, "x64",
                                                                       Architecture.AARCH64, "aarch64"
                                                               ))
                                                       );
                            String replaced = getCodeToDownloadJDK()
                                    .replace("%{JDK_VERSION}%", getJavaReleaseName().get())
                                    .replace("%{JDK_CACHE_DIR}%", getJdkCacheDir().get())
                                    .replace("%{JDK_DOWNLOAD_URL}%", downloadUrl)
                                    .replace(
                                            "%{JDK_DOWNLOAD_URL_M1_MAC}%",
                                            getAppleM1URLOverride().isPresent() ? getAppleM1URLOverride().get() : downloadUrl
                                    );


                            for (OS os : OS.values()) {
                                for (Architecture arch : Architecture.values()) {
                                    replaced = replaced.replace(
                                            "%{CHECKSUM_" + os + "_" + arch + "}%",
                                            checksums.get(os).get(arch)
                                    );
                                }
                            }
                            return line + "\n\n" + replaced
                                    ;
                        } else {
                            return line;
                        }
                    }).collect(Collectors.toList())
            );
        }
        if (!gradlewNewPath.toFile().setExecutable(true)) {
            throw new GradleException("Can't set execute bit on the wrapper");
        }
        Files.move(gradlewNewPath, gradlewPath, StandardCopyOption.REPLACE_EXISTING);
    }

    private String getCodeToDownloadJDK() {
        final InputStream is = getClass().getResourceAsStream("/gradlew-bootstrap-jdk.sh");
        if (is == null) {
            throw new IllegalStateException("Can't find /gradlew-bootstrap-jdk.sh in resources");
        }
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(is))) {
            return String.join("\n", reader.lines().toList());
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

}
