import java.net.URL

plugins {
    val pluginVersion = "0.0.5"
    id("co.elastic.docker-base").version(pluginVersion)
    id("co.elastic.vault").version(pluginVersion)
    id("co.elastic.cli.jfrog").version(pluginVersion)
    //id("co.elastic.elastic-conventions").version(pluginVersion)
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

val creds = vault.readAndCacheSecret("secret/ci/elastic-cloud/artifactory_creds").get()

cli {
    jfrog {
        username.set(creds["username"])
        password.set(creds["plaintext"])
    }
}

dockerBaseImage {
    dockerTagLocalPrefix.set("gradle-test-local")                // configures how the image is imported to the local daemon
    dockerTagPrefix.set("docker.elastic.co/employees/ghHandle")  // configures where the image is pushed
    // Using a mirror is mandatory as older version of pacakges are removed from public mirrors
    osPackageRepository.set(URL("https://${creds["username"]}:${creds["plaintext"]}@artifactory.elastic.dev/artifactory/gradle-plugins-os-packages"))
    fromUbuntu("ubuntu", "20.04")           // Specify the source image, hinting at the distribution
    install("patch")                        // Install the patch utility using the version from the lockfile
    createUser("foobar", 1234, "foobar", 1234)  // generate commands to create a specific user
    copySpec("1234:1234") {                 // Include files and set their ownership
        from(projectDir) {
            include("build.gradle.kts")
        }
        into("home/bar")
    }
    copySpec() {
        from(projectDir) {
            include("build.gradle.kts")
        }
        into("home/foo")
    }
    healthcheck("/home/foobar/foo.sh")
    env("MYVAR_PROJECT" to project.name)
    run(
        "ls -Ral /home",
        "echo \"This plugin rocks on $architecture and ephemeral files are available at $dockerEphemeral!\" > /home/foo/bar.txt"
    )   // Run commands and create a single layer from all
    run(
        listOf(
            "touch /home/foo/run.listOf.1",
            "touch /home/foo/run.listOf.2",
            "chmod -R 777 /home"
        )
    )
    setUser("foobar")                    // configure the default user of the image

    run("whoami > /home/foo/whoami")
}




