package co.elastic.gradle.dockerbase;

import co.elastic.gradle.GradleTestkitHelper;
import co.elastic.gradle.TestkitIntegrationTest;
import org.apache.commons.io.IOUtils;
import org.gradle.testkit.runner.BuildResult;
import org.gradle.testkit.runner.GradleRunner;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.PosixFilePermissions;
import java.util.HashSet;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.fail;

public class DockerBaseImageMatrix extends TestkitIntegrationTest  {

    @ParameterizedTest
    @ValueSource(strings = {"docker.elastic.co/wolfi/chainguard-base:20230214", "ubuntu:20.04", "ubuntu:22.04", "debian:11"})
    // Todo Temp disabled. fix centos base image plugin builds
    // @ValueSource(strings = {"ubuntu:20.04", "ubuntu:22.04", "centos:7", "debian:11"})
    public void testSingleProject(String baseImages, @TempDir Path gradleHome) throws IOException, InterruptedException {
        helper.writeFile("image_content/foo.txt", "sample content");
        writeSimpleBuildScript(helper, baseImages);
        final BuildResult lockfileResult = runGradleTask(gradleRunner, "dockerBaseImageLockfile", gradleHome);

        System.out.println(lockfileResult.getOutput());
        runGradleTask(gradleRunner, "dockerLocalImport", gradleHome);



        System.out.println("Running verification script...");
        Files.copy(
                Objects.requireNonNull(getClass().getResourceAsStream("/test_created_image.sh")),
                helper.projectDir().resolve("test_created_image.sh")
        );
        Files.setPosixFilePermissions(
                helper.projectDir().resolve("test_created_image.sh"),
                PosixFilePermissions.fromString("r-xr-xr-x")
        );
        final Process process = new ProcessBuilder()
                .redirectOutput(ProcessBuilder.Redirect.PIPE)
                .redirectError(ProcessBuilder.Redirect.PIPE)
                .directory(helper.projectDir().toFile())
                .command(helper.projectDir().resolve("test_created_image.sh").toString())
                .start();

        do {
            IOUtils.copy(process.getInputStream(), System.out);
            IOUtils.copy(process.getErrorStream(), System.err);
        } while (process.isAlive());


        final int exitCode = process.waitFor();
        if (exitCode != 0) {
            Assertions.fail("Verification script failed with exit code: " + exitCode);
        }
        System.out.println("Verification script completed successfully...");

        final Set<String> imagesInDaemonRightBeforeClean = getImagesInDaemon();
        final String expectedLocalTag = "gradle-test-local/" + helper.projectDir().getFileName() + "-base:latest";
        if (!imagesInDaemonRightBeforeClean.contains(expectedLocalTag)) {
            fail("Expected " + expectedLocalTag + " to be present in the daemon after local import but it was not");
        }

        runGradleTask(gradleRunner, "dockerBaseImageClean", gradleHome);

        Set<String> imagesInDaemonAfterClean = getImagesInDaemon();
        if (imagesInDaemonAfterClean.contains(expectedLocalTag)) {
            fail("Expected " + expectedLocalTag + " to be not be in the daemon after clean but it was");
        }
    }

    private Set<String> getImagesInDaemon() throws IOException {
        final Process result = new ProcessBuilder()
                .redirectOutput(ProcessBuilder.Redirect.PIPE)
                .redirectError(ProcessBuilder.Redirect.PIPE)
                .command("docker", "image", "ls", "--format", "{{.Repository}}:{{.Tag}}")
                .start();
        Set<String> imagesInDaemon = new HashSet<>();
        do {
            IOUtils.copy(result.getErrorStream(), System.err);
        } while (result.isAlive());
        final BufferedReader lineReader = new BufferedReader(new InputStreamReader(result.getInputStream()));
        for (String line = lineReader.readLine(); line != null; line = lineReader.readLine()) {
            imagesInDaemon.add(line.trim());
        }
        return imagesInDaemon;
    }

    private void writeSimpleBuildScript(GradleTestkitHelper helper, String baseImages) {
        final String[] from = baseImages.split(":");
        assertEquals(2, from.length);
        final String fromType;
        if (baseImages.contains("chainguard")) {
            fromType = "Wolfi";
        } else {
            fromType = from[0].substring(0, 1).toUpperCase() + from[0].substring(1);
        }
        helper.buildScript(String.format("""
                import java.net.URL
                plugins {
                   id("base")
                   id("co.elastic.docker-base")
                   id("co.elastic.cli.jfrog")
                   id("co.elastic.vault")
                }
                vault {
                      address.set("https://vault-ci-prod.elastic.dev")
                      auth {
                        ghTokenFile()
                        ghTokenEnv()
                        tokenEnv()
                        roleAndSecretEnv()
                      }
                }
                val creds = vault.readAndCacheSecret("secret/ci/elastic-gradle-plugins/artifactory_creds").get()
                cli {
                    jfrog {
                        username.set(creds["username"])
                        password.set(creds["plaintext"])
                    }
                }
            
                dockerBaseImage {
                    dockerTagLocalPrefix.set("gradle-test-local")
                    osPackageRepository.set(URL("https://${creds["username"]}:${creds["plaintext"]}@artifactory.elastic.dev/artifactory/gradle-plugins-os-packages"))
                    from%s("%s", "%s")
                    install("patch")
                    copySpec("1234:1234") {
                       from(fileTree("image_content")) {
                          into("home/foobar")
                       }
                    }
                    copySpec() {
                        from(projectDir) {
                           include("build.gradle.kts")
                        }
                        into("home/foobar")
                    }
                    healthcheck("/home/foobar/foo.txt")
                    env("MYVAR_PROJECT" to project.name)
                    createUser("foobar", 1234, "foobar", 1234)
                    run(
                        "ls -Ral /home",
                        "echo \\"This plugin rocks on $architecture and ephemeral files are available at $dockerEphemeral!\\" > /home/foobar/bar.txt"
                    )
                    run(listOf(
                        "touch /home/foobar/run.listOf.1",
                        "touch /home/foobar/run.listOf.2",
                        "chmod -R 777 /home"
                    ))
                    setUser("foobar")
                    install("patch", "sudo", "bash")
                    if ("%s" == "centos") {
                       install("which")
                    }
                    run(
                        "whoami > /home/foobar/whoami",
                    )
                }
                """, fromType, from[0], from[1], from[0])
        );
    }

    private BuildResult runGradleTask(GradleRunner gradleRunner, String task, Path gradleHome) throws IOException {
        try {
            return gradleRunner.withArguments(
                    "--warning-mode", "fail",
                    "-s",
                    "--gradle-user-home", gradleHome.toAbsolutePath().toString(),
                    task
            ).build();
        } finally {
            System.out.println("Listing of project dir:");
            Set<String> fileNamesOfInterest = Set.of("docker-base-image.lock", "Dockerfile", ".dockerignore", "gradle-configuration.list");
            try (Stream<Path> s = Files.walk(helper.projectDir()).filter(each -> !each.toString().contains(".gradle"))) {
                s.forEach(each -> {
                    if (fileNamesOfInterest.contains(each.getFileName().toString())) {
                        System.out.println("Content of: " + helper.projectDir().relativize(each) + "\n");
                        try {
                            IOUtils.copy(Files.newInputStream(each), System.out);
                        } catch (IOException e) {
                            throw new UncheckedIOException(e);
                        }
                        System.out.println("\n----\n");
                    } else {
                        System.out.println(helper.projectDir().relativize(each));
                    }
                });
            }
            System.out.println("Done Listing of project dir");
        }
    }

}
