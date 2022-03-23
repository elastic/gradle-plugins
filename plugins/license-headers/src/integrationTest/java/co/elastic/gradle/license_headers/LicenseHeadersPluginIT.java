package co.elastic.gradle.license_headers;

import co.elastic.gradle.TestkitIntegrationTest;
import org.gradle.testkit.runner.BuildResult;
import org.gradle.testkit.runner.TaskOutcome;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.util.Objects;

import static co.elastic.gradle.AssertContains.assertContains;
import static org.junit.jupiter.api.Assertions.assertEquals;

class LicenseHeadersPluginIT extends TestkitIntegrationTest {

    @BeforeEach
    void before() {
        helper.writeFile("src/header.txt", """
                // This is a sample header
                // that has multiple lines
                // and should be present in each file
                """);

        // Build dir should be ignored, we write a file without a header to prove it
        helper.writeFile("build/sample.java", "\n\n\n");
    }

    @Test
    public void failIfNoConfig() {
        helper.buildScript("""
                plugins {
                    id("co.elastic.license-headers")
                }
                licenseHeaders {
                
                }
                """);
        final BuildResult result = gradleRunner
                .withArguments("--warning-mode", "fail", "-s", "checkLicenseHeaders")
                .buildAndFail();

        assertContains(
                result.getOutput(),
                "No license header configurations defined"
        );
    }

    @Test
    public void defaultHeaderCheckPasses() {
        helper.writeFile("src/sample.java", """
                // This is a sample header
                // that has multiple lines
                // and should be present in each file
                
                Then there are other lines,
                of course not java, but here to prove that things still work.
                """);
        helper.buildScript("""
                plugins {
                    id("co.elastic.license-headers")
                }
                licenseHeaders {
                    check(fileTree(projectDir)) {
                        exclude("build.gradle.kts")
                    }
                }
                """);
        final BuildResult result = gradleRunner
                .withArguments("--warning-mode", "fail", "checkLicenseHeaders")
                .build();

        // The header file itself is scanned too
        assertContains(
                result.getOutput(),
                "Scanning 2 files for headers"
        );

        final BuildResult resultAgain = gradleRunner
                .withArguments("--warning-mode", "fail", "checkLicenseHeaders")
                .build();

        assertEquals(TaskOutcome.UP_TO_DATE, Objects.requireNonNull(resultAgain.task(":checkLicenseHeaders")).getOutcome());
    }

    @Test
    public void integrationsWithLifecycle() {
        helper.writeFile("src/sample.java", """
                // This is a sample header
                // that has multiple lines
                // and should be present in each file
                                
                Then there are other lines,
                of course not java, but here to prove that things still work.
                """);
        helper.buildScript("""
                plugins {
                    id("co.elastic.license-headers")
                    id("co.elastic.lifecycle")
                    id("co.elastic.lifecycle-multi-arch")
                }
                licenseHeaders {
                    check(fileTree(projectDir)) {
                        exclude("build.gradle.kts")
                    }
                }
                """);
        final BuildResult result = gradleRunner
                .withArguments("--warning-mode", "fail", "check")
                .build();

        // The header file itself is scanned too
        assertContains(
                result.getOutput(),
                "Scanning 2 files for headers"
        );

        final BuildResult resultAgain = gradleRunner
                .withArguments("--warning-mode", "fail", "checkPlatformIndependent")
                .build();

        assertEquals(TaskOutcome.UP_TO_DATE, Objects.requireNonNull(resultAgain.task(":checkLicenseHeaders")).getOutcome());
    }

        @Test
    public void alternateHeaderFile() {
        helper.writeFile("header.txt", "// Alternate header");
        helper.writeFile("src/sample.java", """
                // Alternate header
                
                Then there are other lines,
                of course not java, but here to prove that things still work.
                """);
        helper.buildScript("""
                plugins {
                    id("co.elastic.license-headers")
                }
                licenseHeaders {
                    check(fileTree("src")) {
                      exclude("**/*.txt")
                      headerFile.set(file("header.txt"))
                    }
                }
                """);
        final BuildResult result = gradleRunner
                .withArguments("--warning-mode", "fail", "checkLicenseHeaders")
                .build();

        assertContains(
                result.getOutput(),
                "Scanning 1 files for headers"
        );
    }

    @Test
    public void failIfHeaderNotPresent() {
        helper.writeFile("src/sample.java", """
                Then there are other lines,
                of course not java, but here to prove that
                the missing header is correctly detected
                """);
        helper.buildScript("""
                plugins {
                    id("co.elastic.license-headers")
                }
                licenseHeaders {
                    check(fileTree(projectDir)) {
                        exclude("build.gradle.kts")
                    }
                }
                """);
        final BuildResult result = gradleRunner
                .withArguments("--warning-mode", "fail", "checkLicenseHeaders")
                .buildAndFail();

        // The header file itself is scanned too
        assertContains(
                result.getOutput(),
                "src/sample.java : Missing header"
        );

        BuildResult fixHeaders = gradleRunner
                .withArguments("--warning-mode", "fail", "fixLicenseHeaders")
                .build();

        assertEquals(TaskOutcome.SUCCESS, Objects.requireNonNull(fixHeaders.task(":fixLicenseHeaders")).getOutcome());

        final BuildResult fixedResult = gradleRunner
                .withArguments("--warning-mode", "fail", "checkLicenseHeaders")
                .build();

        // The header file itself is scanned too
        assertContains(
                fixedResult.getOutput(),
                "Scanning 2 files for headers"
        );
    }

    @Test
    public void failWithPartialHeader() throws IOException {
        helper.writeFile("src/sample.java", """
                // This is a sample header
                // that has multiple lines!?@
                // and should be present in each file
                
                Then there are other lines,
                of course not java, but here to prove that things still work.
                """);
        helper.buildScript("""
                plugins {
                    id("co.elastic.license-headers")
                }
                licenseHeaders {
                    check(fileTree(projectDir)) {
                        exclude("build.gradle.kts")
                    }
                }
                """);
        final BuildResult result = gradleRunner
                .withArguments("--warning-mode", "fail", "checkLicenseHeaders")
                .buildAndFail();

        // The header file itself is scanned too
        assertContains(
                result.getOutput(),
                "src/sample.java : Header mismatch at line 2"
        );

        gradleRunner
                .withArguments("--warning-mode", "fail", "fixLicenseHeaders")
                .build();

        final BuildResult fixedResult = gradleRunner
                .withArguments("--warning-mode", "fail", "checkLicenseHeaders")
                .buildAndFail();

        // The header file itself is scanned too
        assertContains(
                fixedResult.getOutput(),
                "Scanning 2 files for headers"
        );
    }

    @Test
    public void failWithSmallFile() throws IOException {
        helper.writeFile("src/sample.java", "\n");
        helper.buildScript("""
                plugins {
                    id("co.elastic.license-headers")
                }
                licenseHeaders {
                    check(fileTree(projectDir)) {
                        exclude("build.gradle.kts")
                    }
                }
                """);
        final BuildResult result = gradleRunner
                .withArguments("--warning-mode", "fail", "checkLicenseHeaders")
                .buildAndFail();

        // The header file itself is scanned too
        assertContains(
                result.getOutput(),
                "src/sample.java : File has fewer lines than the header"
        );

        BuildResult cantFix = gradleRunner
                .withArguments("--warning-mode", "fail", "fixLicenseHeaders")
                .build();

        assertContains(
                cantFix.getOutput(),
                "Added header to src/sample.java"
        );

        final BuildResult fixedResult = gradleRunner
                .withArguments("--warning-mode", "fail", "checkLicenseHeaders")
                .build();

        // The header file itself is scanned too
        assertContains(
                result.getOutput(),
                "Scanning 2 files for headers"
        );
    }


    @Test
    public void skipWithNoSourceIfNothingToScan() {
        writeDefaultHeader();
        helper.writeFile("src/sample.java", """
                // This is a sample header
                // that has multiple lines
                // and should be present in each file
                
                Then there are other lines,
                of course not java, but here to prove that things still work.
                """);
        helper.buildScript("""
                plugins {
                    id("co.elastic.license-headers")
                }
                licenseHeaders {
                    check(fileTree(projectDir)) {
                        exclude("build.gradle.kts")
                        exclude("src/**")
                    }
                }
                """);
        final BuildResult result = gradleRunner
                .withArguments("--warning-mode", "fail", "checkLicenseHeaders")
                .build();

        assertEquals(TaskOutcome.NO_SOURCE, Objects.requireNonNull(result.task(":checkLicenseHeaders")).getOutcome());
    }

    private void writeDefaultHeader() {
        helper.writeFile("src/header.txt", """
                // This is a sample header
                // that has multiple lines
                // and should be present in each file
                """);
    }

}