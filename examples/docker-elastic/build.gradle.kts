import java.net.URL

plugins {
    id("co.elastic.docker-base")
    id("co.elastic.elastic-conventions")
    id("co.elastic.docker-component")
}

dockerBaseImage {
    dockerTagLocalPrefix.set("gradle-test-local")                // configures how the image is imported to the local daemon
    dockerTagPrefix.set("docker.elastic.co/employees/ghHandle")  // configures where the image is pushed

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

dockerComponentImage {
    buildAll {
        from(project)
    }
}
