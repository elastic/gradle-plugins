/*
 * Copyright Elasticsearch B.V. and/or licensed to Elasticsearch B.V. under one
 * or more contributor license agreements. See the NOTICE file distributed with
 * this work for additional information regarding copyright
 * ownership. Elasticsearch B.V. licenses this file to you under
 * the Apache License, Version 2.0 (the "License"); you may
 * not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *	http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied.  See the License for the
 * specific language governing permissions and limitations
 * under the License.
 */
package co.elastic.gradle.dockerbase;

import co.elastic.gradle.TestkitIntegrationTest;
import co.elastic.gradle.utils.Architecture;
import org.apache.commons.io.IOUtils;
import org.gradle.testkit.runner.BuildResult;
import org.gradle.testkit.runner.TaskOutcome;
import org.junit.jupiter.api.Test;
import org.w3c.dom.Document;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;
import org.xml.sax.SAXException;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;
import javax.xml.transform.OutputKeys;
import javax.xml.transform.Transformer;
import javax.xml.transform.TransformerException;
import javax.xml.transform.TransformerFactory;
import javax.xml.transform.dom.DOMSource;
import javax.xml.transform.stream.StreamResult;
import javax.xml.xpath.XPath;
import javax.xml.xpath.XPathConstants;
import javax.xml.xpath.XPathExpressionException;
import javax.xml.xpath.XPathFactory;
import java.io.IOException;
import java.io.StringWriter;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Stream;

import static co.elastic.gradle.AssertContains.assertContains;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

public class DockerBaseImageBuildPluginIT extends TestkitIntegrationTest {

    @Test
    public void testMultiProject() throws IOException {
        helper.settings("""
                include("s1")
                include("s2")
                include("s3")
                """
        );
        helper.buildScript(String.format("""
                import java.net.URL
                import %s
                                
                                                            
                plugins {
                   id("co.elastic.vault")
                   id("co.elastic.cli.jfrog")
                   id("co.elastic.docker-base").apply(false)
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
                                
                subprojects {
                    apply(plugin = "co.elastic.docker-base")
                    print(project.name)
                    configure<BaseImageExtension> {
                        osPackageRepository.set(URL("https://${creds["username"]}:${creds["plaintext"]}@artifactory.elastic.dev/artifactory/gradle-plugins-os-packages"))  
                    }
                }
                """, BaseImageExtension.class.getName()
        ));

        helper.buildScript("s1", """
                plugins {
                   id("co.elastic.vault")
                   id("co.elastic.cli.jfrog")
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
                  fromUbuntu("ubuntu", "20.04")
                  install("patch")
                }
                """
        );
        helper.buildScript("s2", """
                plugins {
                   id("co.elastic.vault")
                   id("co.elastic.cli.jfrog")
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
                  from(project(":s1"))
                  run("patch --version")
                  install("curl")
                }
                """
        );
        helper.buildScript("s3", """
                plugins {
                   id("co.elastic.vault")
                   id("co.elastic.cli.jfrog")
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
                  from(project(":s2"))
                  run("curl --version")
                }
                """
        );

        runGradleTask(":s1:dockerBaseImageLockfile");
        runGradleTask(":s2:dockerBaseImageLockfile");
        runGradleTask(":s3:dockerBaseImageLockfile");
        runGradleTask("dockerLocalImport");
    }

    @Test
    public void testPullTask() throws IOException {
        Files.createDirectories(helper.projectDir().resolve("s1"));
        Files.copy(
                Objects.requireNonNull(getClass().getResourceAsStream("/ubuntu.lockfile.yaml")),
                helper.projectDir().resolve("s1/docker-base-image.lock")
        );
        helper.settings("""
                include("s1")
                include("s2")
                """
        );
        helper.buildScript("s1", """
                plugins {
                    id("co.elastic.docker-base")
                }
                dockerBaseImage {
                      fromUbuntu("ubuntu", "20.04")
                }
                """
        );
        helper.buildScript("s2", """
                plugins {
                    id("co.elastic.docker-base")
                }
                dockerBaseImage {
                    from(project(":s1"))
                }
                """
        );
        final BuildResult result = gradleRunner.withArguments("--warning-mode", "fail", "-s", "dockerBasePull").build();
        assertContains(result.getOutput(), "Pulling from library/ubuntu");
        assertContains(
                result.getOutput(),
                Architecture.current().map(Map.of(
                        Architecture.AARCH64, "sha256:9bfe2c7a24b46c861ffea8b27dd1015e3b52e93e5581a09eacecd5a3cd601924",
                        Architecture.X86_64, "sha256:cc9cc8169c9517ae035cf293b15f06922cb8c6c864d625a72b7b18667f264b70"
                ))
        );
        assertEquals(TaskOutcome.SKIPPED, Objects.requireNonNull(result.task(":s2:dockerBasePull")).getOutcome());
    }

    @Test
    public void testDockerEphemeralConfig() throws IOException {
        helper.buildScript("""
                import java.net.URL
                plugins {
                   id("co.elastic.docker-base")
                   id("co.elastic.vault")
                   id("co.elastic.cli.jfrog")
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
                repositories {
                    mavenCentral()
                }
                dependencies {
                   dockerEphemeral("org.slf4j:slf4j-api:1.7.36")
                }
                val creds = vault.readAndCacheSecret("secret/ci/elastic-gradle-plugins/artifactory_creds").get()
                cli {
                    jfrog {
                        username.set(creds["username"])
                        password.set(creds["plaintext"])
                    }
                }
                dockerBaseImage {
                    osPackageRepository.set(URL("https://${creds["username"]}:${creds["plaintext"]}@artifactory.elastic.dev/artifactory/gradle-plugins-os-packages"))
                    fromUbuntu("ubuntu", "20.04")
                    run(
                        "ls $dockerEphemeral/slf4j-api-1.7.36.jar",
                    )
                }
                """
        );
        Files.copy(
                Objects.requireNonNull(getClass().getResourceAsStream("/ubuntu.lockfile.yaml")),
                helper.projectDir().resolve("docker-base-image.lock")
        );
        final BuildResult result = gradleRunner.withArguments("--warning-mode", "fail", "-s", "dockerBaseImageBuild").build();
        assertContains(result.getOutput(), "slf4j-api-1.7.36.jar");
    }

    private BuildResult runGradleTask(String task) throws IOException {
        try {
            return gradleRunner.withArguments("--warning-mode", "fail", "-s", task).build();
        } finally {
            System.out.println("Listing of project dir:");
            Set<String> fileNamesOfInterest = Set.of("docker-base-image.lock", "Dockerfile", ".dockerignore", "gradle-configuration.list", "verification-metadata.xml");
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

    @Test
    public void testLockfileWithEmulation() throws IOException {
        assumeTrue(
                System.getenv().getOrDefault("BUILDKITE", "false").equals("false"),
                "Test will be skipped in CI because there's no emulation support"
        );
        helper.buildScript("""
        import java.net.URL
            plugins {
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
                osPackageRepository.set(URL("https://${creds["username"]}:${creds["plaintext"]}@artifactory.elastic.dev/artifactory/gradle-plugins-os-packages"))
                fromUbuntu("ubuntu", "20.04")
                install("patch")
            }
        """);
        final BuildResult result = runGradleTask("dockerBaseImageLockfileAllWithEmulation");
            System.out.println(result.getOutput());
        assertNotNull(result.task(":dockerBaseImageLockfile"), "Expected task dockerBaseImageLockfile to have run");
        switch (Architecture.current()) {
            case X86_64 -> {
                assertNotNull(result.task(":dockerBaseImageLockfilearm64"), "Expected task dockerBaseImageLockfilearm64 to have run.");
            }
            case AARCH64 -> {
                assertNotNull(result.task(":dockerBaseImageLockfileamd64"), "Expected task dockerBaseImageLockfileamd64 to have run.");
            }
        }

    }

    @Test
    public void testLockfileWithEmulationWolfi() throws IOException {
        assumeTrue(
                System.getenv().getOrDefault("BUILDKITE", "false").equals("false"),
                "Test will be skipped in CI because there's no emulation support"
        );
        helper.buildScript("""
        import java.net.URL
            plugins {
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
                osPackageRepository.set(URL("https://${creds["username"]}:${creds["plaintext"]}@artifactory.elastic.dev/artifactory/gradle-plugins-os-packages"))
                fromWolfi("docker.elastic.co/wolfi/chainguard-base", "20230214")
                install("patch")
            }
        """);
        final BuildResult result = runGradleTask("dockerBaseImageLockfileAllWithEmulation");
        System.out.println(result.getOutput());
        assertNotNull(result.task(":dockerBaseImageLockfile"), "Expected task dockerBaseImageLockfile to have run");
        switch (Architecture.current()) {
            case X86_64 -> {
                assertNotNull(result.task(":dockerBaseImageLockfilearm64"), "Expected task dockerBaseImageLockfilearm64 to have run.");
            }
            case AARCH64 -> {
                assertNotNull(result.task(":dockerBaseImageLockfileamd64"), "Expected task dockerBaseImageLockfileamd64 to have run.");
            }
        }

    }

    @Test
    public void testVerificationMetadata() throws IOException, XPathExpressionException, ParserConfigurationException, TransformerException, SAXException {
        Files.copy(
                Objects.requireNonNull(getClass().getResourceAsStream("/ubuntu.lockfile.yaml")),
                helper.projectDir().resolve("docker-base-image.lock")
        );

        helper.buildScript("""
        import java.net.URL                      
        
            plugins {
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
                osPackageRepository.set(URL("https://${creds["username"]}:${creds["plaintext"]}@artifactory.elastic.dev/artifactory/gradle-plugins-os-packages"))
                fromUbuntu("ubuntu", "20.04")
                install("patch")
            }
        """);




        // Test that the dependencies from the lock-file are added to verification metadata

        System.out.println(
                gradleRunner.withArguments(
                        "--warning-mode", "fail",
                        "-s",
                        "--write-verification-metadata", "sha256,sha512",
                        "help"
                ).build().getOutput()
        );

        System.out.println(runGradleTask("dependencies").getOutput());

        assertVerificationMetaDataContainsOsPackages(
                helper.projectDir().resolve("gradle/verification-metadata.xml")
        );
    }

    private void assertVerificationMetaDataContainsOsPackages(Path verificationData) throws IOException, ParserConfigurationException, SAXException, XPathExpressionException, TransformerException {
        DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
        factory.setNamespaceAware(true);
        DocumentBuilder builder = factory.newDocumentBuilder();
        Document document = builder.parse(Files.newInputStream(verificationData));

        XPathFactory xPathFactory = XPathFactory.newInstance();
        XPath xpath = xPathFactory.newXPath();
        String expression = "//*[local-name()='component'][@group='ubuntu' and @name='patch']";
        NodeList components = (NodeList) xpath.evaluate(expression, document, XPathConstants.NODESET);


        System.out.println(" == Verification metadata xml ==");
        System.out.println("-- Found components: --");
        for (int i = 0; i < components.getLength(); i++) {
            Node component = components.item(i);
            System.out.println(nodeToString(component));
        }

        assertEquals(2, components.getLength(), "Expected to find 2 components for patch in verification metadata.");
    }

    private String nodeToString(Node node) throws TransformerException {
        Transformer transformer = TransformerFactory.newInstance().newTransformer();
        transformer.setOutputProperty(OutputKeys.OMIT_XML_DECLARATION, "yes");
        transformer.setOutputProperty(OutputKeys.INDENT, "yes");
        StringWriter writer = new StringWriter();
        transformer.transform(new DOMSource(node), new StreamResult(writer));
        return writer.toString();
    }

}
