package co.elastic.gralde.wrapper;

import co.elastic.gradle.AssertContains;
import co.elastic.gradle.TestkitIntegrationTest;
import co.elastic.gradle.utils.Architecture;
import co.elastic.gradle.utils.OS;
import org.apache.commons.io.IOUtils;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;

class WrapperPluginIT extends TestkitIntegrationTest {

    @Test
    void apply() throws IOException, InterruptedException {
        helper.buildScript(String.format("""
                            import %s.*
                            import %s.*
                            plugins {
                                id("co.elastic.wrapper-provision-jdk")
                            }
                            tasks.wrapperProvisionJdk {
                                jdkCacheDir.set("$\\{JENKINS_HOME:-$HOME\\}/.gradle/jdks")
                                javaReleaseName.set("11.0.13+8")
                                appleM1URLOverride.set(
                                    "https://download.bell-sw.com/java/11.0.13+8/bellsoft-jdk11.0.13+8-macos-aarch64.tar.gz"
                                )
                                checksums.set(
                                  mapOf(
                                     LINUX to mapOf(
                                       X86_64 to "3b1c0c34be4c894e64135a454f2d5aaa4bd10aea04ec2fa0c0efe6bb26528e30",
                                       AARCH64 to "a77013bff10a5e9c59159231dd5c4bd071fc4c24beed42bd49b82803ba9506ef"
                                     ),
                                     DARWIN to mapOf(
                                       X86_64 to "2b862f97b872e37f8c7ad6d3d30f7d0fcb3f0b951740c8fa142dea702945973c",
                                       AARCH64 to "7dce00825d5ff0d6f2d39fa1add59ce7f4eefee5b588981b43708d00c43f4f9b"
                                     )
                                  )
                                )
                            }
                        """,
                OS.class.getName(), Architecture.class.getName()
        ));

        gradleRunner.withArguments("--warning-mode", "fail", "-s", "wrapper")
                .build();

        final String gradlewContent = Files.readString(helper.projectDir().resolve("gradlew"));
        AssertContains.assertContains(
                gradlewContent,
                "https://api.adoptium.net/v3/binary/version/"
        );
        AssertContains.assertDoesNotContain(
                gradlewContent,
                "%{"
        );

        // Remove the build-script so it won't fail on finding the plugin as it's running without testkit this time
        helper.buildScript("");

        final Process process = new ProcessBuilder()
                .directory(helper.projectDir().toFile())
                .command(helper.projectDir().resolve("gradlew").toAbsolutePath().toString(), "help", "--no-scan")
                .redirectOutput(ProcessBuilder.Redirect.PIPE)
                .redirectError(ProcessBuilder.Redirect.PIPE)
                .start();

        do {
            IOUtils.copy(process.getInputStream(), System.out);
            IOUtils.copy(process.getErrorStream(), System.err);
        } while (process.isAlive());


        final int exitCode = process.waitFor();
        if (exitCode != 0) {
            Assertions.fail("Failed to run generated wrapper " + exitCode + "\n" + gradlewContent);
        }

    }
}