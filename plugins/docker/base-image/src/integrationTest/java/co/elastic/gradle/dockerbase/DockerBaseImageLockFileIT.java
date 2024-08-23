package co.elastic.gradle.dockerbase;

import co.elastic.gradle.TestkitIntegrationTest;
import co.elastic.gradle.utils.Architecture;
import org.apache.commons.io.IOUtils;
import org.gradle.testkit.runner.BuildResult;
import org.gradle.testkit.runner.BuildTask;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.Set;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assumptions.assumeTrue;

public class DockerBaseImageLockFileIT extends TestkitIntegrationTest {

    @BeforeEach
    public void checkEmulation() {
        assumeTrue(
                System.getenv().getOrDefault("BUILDKITE", "false").equals("false"),
                "Test will be skipped in CI because there's no emulation support"
        );
    }

    @Test
    public void testLockfileWithEmulationWolfiFromProject() throws IOException {
        helper.settings("""
                include("p1")
                include("p2")
                """);
        helper.buildScript("evaluationDependsOnChildren()");
        helper.buildScript("p1", """
                    import java.net.URL
                    plugins {
                        id("co.elastic.cli.jfrog")
                        id("co.elastic.vault")
                        id("co.elastic.docker-base")
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
                        osPackageRepository.set(
                            URL("https://${creds["username"]}:${creds["plaintext"]}@artifactory.elastic.dev/artifactory/gradle-plugins-os-packages")
                        )
                        dockerTagLocalPrefix.set("docker.elastic.co/gradle")
                        dockerTagPrefix.set("docker.elastic.co/cloud-ci")
                        fromWolfi("docker.elastic.co/wolfi/chainguard-base", "latest")
                    }
                    """);
        helper.buildScript("p2", """
                    import java.net.URL
                    plugins {
                        id("co.elastic.cli.jfrog")
                        id("co.elastic.vault")
                        id("co.elastic.docker-base")
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
                        osPackageRepository.set(
                            URL("https://${creds["username"]}:${creds["plaintext"]}@artifactory.elastic.dev/artifactory/gradle-plugins-os-packages")
                        )
                        dockerTagLocalPrefix.set("docker.elastic.co/gradle")
                        dockerTagPrefix.set("docker.elastic.co/cloud-ci")
                        from(project(":p1"))
                    }
                    """);
        System.out.println(runGradleTask("p1:dockerBaseImageLockfileAllWithEmulation").getOutput());

        final BuildResult result = runGradleTask("p2:dockerBaseImageLockfileAllWithEmulation");
        System.out.println(result.getOutput());

        final BuildResult dockerBaseImageBuild = runGradleTask("dockerBaseImageBuild");
        System.out.println("==== dockerBaseImageBuild ====");
        System.out.println(dockerBaseImageBuild.getOutput());
        //Building the image should not build the emulation tasks
        Arrays.stream(Architecture.values()).map(Architecture::dockerName).forEach(arch ->
                {
                    Assertions.assertFalse(
                            dockerBaseImageBuild.getTasks().stream().map(BuildTask::getPath).anyMatch(it -> it.contains(arch)),
                            "Expected that with dockerBaseImageBuild tasks requiring emulation did not run, but they did"
                    );
                }
        );

        final BuildResult dockerBaseImageLocalImport = runGradleTask("dockerBaseImageLocalImport");
        System.out.println("==== dockerBaseImageLocalImport ====");
        System.out.println(dockerBaseImageLocalImport.getOutput());
        //Building the image should not build the emulation tasks
        Arrays.stream(Architecture.values()).map(Architecture::dockerName).forEach(arch ->
                {
                    Assertions.assertFalse(
                            dockerBaseImageLocalImport.getTasks().stream().map(BuildTask::getPath).anyMatch(it -> it.contains(arch)),
                            "Expected that with dockerBaseImageLocalImport tasks requiring emulation did not run, but they did"
                    );
                }
        );

    }

    private BuildResult runGradleTask(String task) throws IOException {
        try {
            return gradleRunner.withArguments(
                    "--warning-mode", "fail",
                    "-s",
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
