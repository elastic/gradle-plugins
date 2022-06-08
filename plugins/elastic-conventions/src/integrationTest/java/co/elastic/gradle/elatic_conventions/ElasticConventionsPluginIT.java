package co.elastic.gradle.elatic_conventions;

import co.elastic.gradle.TestkitIntegrationTest;
import co.elastic.gradle.vault.VaultExtension;
import org.gradle.testkit.runner.BuildResult;
import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

import static co.elastic.gradle.AssertContains.assertContains;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

public class ElasticConventionsPluginIT extends TestkitIntegrationTest {

    @Test
    public void withLifecycle() {
        helper.settings("""
        plugins {
            id("co.elastic.elastic-conventions")
        }    
        """);
        helper.buildScript("""
        plugins {
            id("co.elastic.elastic-conventions")
        }    
        """);

        final BuildResult result = gradleRunner
                .withArguments("--warning-mode", "fail", "-s", "check")
                .build();
        System.out.println(result.getOutput());
    }

    @Test
    public void withVault() {
        final Path ghTokenPath = Paths.get(System.getProperty("user.home") + "/.elastic/github.token");
        assumeTrue(
                Files.exists(ghTokenPath),
                "Test will be skipped unless a GH token is present at " + ghTokenPath
        );
        helper.settings(String.format("""
                   import %s
                   plugins {
                       id("co.elastic.vault")
                       id("co.elastic.elastic-conventions")
                   }
                   
                   val vault = the<VaultExtension>()
                   logger.lifecycle("settings secret is {}", vault.readSecret("secret/cloud-team/cloud-ci/gradle-vault-integration").get()["key1"])
                   logger.lifecycle("settings secret cached is {}", vault.readAndCacheSecret("secret/cloud-team/cloud-ci/gradle-vault-integration").get()["key1"])
                """, VaultExtension.class.getName())
        );
        helper.buildScript(String.format("""
                   import %s
                   plugins {
                       id("co.elastic.vault")
                       id("co.elastic.elastic-conventions")
                   }
                                  
                   logger.lifecycle("build secret is {}", vault.readSecret("secret/cloud-team/cloud-ci/gradle-vault-integration").get()["key1"])
                   logger.lifecycle("build secret cached is {}", vault.readAndCacheSecret("secret/cloud-team/cloud-ci/gradle-vault-integration").get()["key1"])
                """, VaultExtension.class.getName())
        );

        final BuildResult result = gradleRunner
                .withArguments("--warning-mode", "fail", "-s", "help")
                .build();

        assertContains(result.getOutput(), "settings secret is test");
        assertContains(result.getOutput(), "settings secret cached is test");

        System.out.println(result.getOutput());
    }




}
