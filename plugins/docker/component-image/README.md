Docker Component Image Build Plugin
====================================

About
-----

This plugin complements the [Base ImagePlugin](../base-image/README.md)
by providing a fast, cross-platform way of building multi-platform docker images meant to run a service/application. It
uses
[jib-core](https://github.com/GoogleContainerTools/jib/tree/master/jib-core)
under the hood, and draws inspiration from the
[jib-gradle-plugin](https://github.com/GoogleContainerTools/jib/tree/master/jib-gradle-plugin), but implements a more
flexible, less opinionated DSL that allows for more customization in the resulting image.

This means that you can build and push e.g. an am64 image, and arm64 one for you application and create a manifest list
for it to be referenced seamlessly and allow docker to pick the right one depending the platform it's running on. This
can be done for platform dependent applications too (e.g. native executables, dynamic libraries etc.) as you can
selectively add them depending on the architecture of the image. This works regardless of where the build is running
(e.g. Linux and Mac on amd64 and arm64 each ar supported).

The plugin also works with the [Sandbox Plugin](../../sandbox/README.md) to make it easy to run the resulting
containres, and integrates with the [Snyk CLI PLugin](../../cli/README.md) to run security scans on the resulting image
using `snyc`.

Usage
-----

### Building an image from a static base image

Note: We need to configure the [Cli Plugin](../../cli/README.md) to be able to push manifest lists, as for the time the
plugin uses `manifest-tool` under the hood.

```kotlin
plugins {
    id("co.elastic.docker-component")
    id("co.elastic.vault")
}
vault {
    address.set("https://secrets.elastic.co:8200")
    auth {
        ghTokenFile()
        ghTokenEnv()
        tokenEnv()
        roleAndSecretEnv()
    }
}
cli {
    manifestTool {
        val credentials = vault.readAndCacheSecret("secret/cloud-team/cloud-ci/artifactory_creds").get()
        username.set(credentials["username"])
        password.set(credentials["plaintext"])
    }
}

dockerComponentImage {
    dockerTagLocalPrefix.set("gradle-test-local")                // optional, configures how the image is imported to the local daemon
    dockerTagPrefix.set("docker.elastic.co/employees/ghHandle")  // optional, configures where the image is pushed 
    buildAll {
        from("ubuntu", "20.04")
        maintainer("Jon Doe", "jon.doe@email.com")
        // Add an architecture specific component and rename it to drop the architecture from the name
        copySpec("1000:1000") {
            from("$buildDir/bin/component-$architecture")
            into("home")
            rename { "component" }
        }
        entryPoint(listOf("/home/component"))
        cmd(listOf("default", "cmd", "args"))
        env("MY_ENV_VAR" to "BAR")
        workDir("/home")
        exposeTcp(80)
        exposeUdp(80)
        label("foo" to "bar")
        changingLabel("random" to Random.nextInt(0, 10000).toString())
    }
}
```

Before the first time is built, or any time one wants to pick up a newer base image:

```shell
./gradlew dockerComponentLockFile
```

This will create a lock-file that that is meant to be checked in and offers a way to control when the base image is
updated. Running the command again will always get the latest repo digests pointed to by the tag. This approach works
better with build avoidance and is more predictable as one can control when updates are picked up as opposed to maybe
getting them right before or while preparing for a release. For convenience and to make sure it's not forgotten, this
functionality can be used in automation that will re-generate the lock-file and open a PR with the result.

To build the image into the local daemon:

```shell
./gradlew dockerComponentImageLocalImport
```

To push images for all platforms and a manifest list:

```shell
./graldew pushManifestList
```

Credentials used are the ones configured for `docker login`.

### Building an image from a base image built in the same build

Occasionally, one might need to customize the base image in a specific way for the component, e.g. to install some
operating system package dependencies, or customize the image in a way that is not supported by the plugin and thus
requires a script to run. To support this the component image can use an image created with the
[Docker Base Image Plugin](../base-image/README.md).

```kotlin
import java.net.URL

plugins {
    id("co.elastic.docker-component")
    id("co.elastic.docker-base")
    id("co.elastic.vault")
}
project.version = "myversion"
vault {
    address.set("https://secrets.elastic.co:8200")
    auth {
        ghTokenFile()
        ghTokenEnv()
        tokenEnv()
        roleAndSecretEnv()
    }
}
cli {
    manifestTool {
        val credentials = vault.readAndCacheSecret("secret/cloud-team/cloud-ci/artifactory_creds").get()
        username.set(credentials["username"])
        password.set(credentials["plaintext"])
    }
}
val creds = vault.readAndCacheSecret("secret/cloud-team/cloud-ci/artifactory_creds").get()
dockerBaseImage {
    dockerTagPrefix.set("docker.elastic.co/employees/alpar-t")
    mirrorBaseURL.set(URL("https://${creds["username"]}:${creds["plaintext"]}@artifactory.elastic.dev/artifactory/"))
    fromUbuntu("ubuntu", "20.04")
    install("patch")
}
dockerComponentImage {
    buildOnly(listOf(Architecture.current())) {
        dockerTagPrefix.set("docker.elastic.co/employees/alpar-t")
        from(project)
    }
}
```

#### copySpec([owner UID:owner GUID])

Add files to the container. Similar to a Gradle [Copy] task. Check out the Gradle docs
on [working with files](https://docs.gradle.org/current/userguide/working_with_files.html)
for more information on how to use it.

### Building the image locally

To import the image into the local daemon run as per usual:

```shell
./gradlew dockerComponentImageLocalImport
```

This will work because the local import only really builds an image for the current architecture.

### Adding dynamically generated content

One might want to add something to the container image that is generated as part of the same build. Since the image
contents is defined in an extension, there's no `dependsOn`, so this can't work as a regular dependency.

To make it work, one can pass a task in the `from` instruction of a `copySpec`. For built-in tasks that define their
outputs this is all it takes. Custom tasks need to be explicit about their outputs:

```kotlin
val archive by tasks.registering(Zip::class) {
    from(fileTree("src"))
    archiveFileName.set("my.zip")
    destinationDirectory.set(layout.buildDirectory.dir("dist"))
}
val custom by tasks.registering {
    outputs.file("$buildDir/some_output.txt")
    doLast {
        // generate some_output.txt 
    }
}
dockerComponentImage {
    buildAll {
        from("ubuntu", "20.04")
        copySpec {
            from(archive)
            from(custom)
            into("home")
        }
    }
}
```

#### Notes on CI setup and pushing a manifest

In CI, or when pushing a manifest, the plugin assumes that the base images of the current version are already pushed.
This is because the base image plugin does not support emulation and thus, unlike the component plugin is not able to
build the images for all the required architectures (base images require actually executing architecture dependent code
that is not possible without emulation).

This means that a typical CI setup will have workers for all the required architectures and will build and push the base
images on each before it will build and push the component images. For the lather any CI worker will work.

When working locally, the simplest thing is to import into the local daemon and work with that. Alternatively one needs
to simulate or use CI infrastructure to build the base images for the change before the component images can be built.

### Building on specific platforms only

It could happen that the base image does not have variants for all the platforms supported by the plugin. e.g. an image
available on amd64 only. In this case the platforms to build on can be

```kotlin
import co.elastic.gradle.utils.Architecture

dockerComponentImage {
    buildOnly(listOf(Architecture.X86_64)) {
        from("legacy_image", "latest")
    }
    configure {
        // additional configuration
    }
}
```

### Security scanning

To be able to run security scans, configure the `snyk` tool and plugin:

```kotlin
import co.elastic.gradle.snyk.SnykCLIExecTask

cli {
    snyk {
        val credentials = vault.readAndCacheSecret("secret/cloud-team/cloud-ci/artifactory_creds").get()
        username.set(credentials["username"])
        password.set(credentials["plaintext"])
    }
}
tasks.withType<SnykCLIExecTask> {
    environment(
        "SNYK_TOKEN",
        vault.readAndCacheSecret("secret/cloud-team/cloud-ci/snyk_api_key").get()["plaintext"].toString()
    )
}
```

To run a scan and see results locally:

```shell
./gradlew dockerComponentImageScanLocal
```

The task fails if any security vulnerabilities are found.

To register results with snyk (`snyk monitor`) run:

```shell
./gradlew dockerComponentImageScan
```

This assumes that the manifest list was pushed and runs the scan against the image in the registry for the current
architecture. The task doesn't fail if security vulnerabilities are found and results are visible in the snyk UI.

### Running the resulting containers

The existing containers can be run using the [Sandbox Plugin](../../sandbox/README.md):

```kotlin
tasks.register<SandboxDockerExecTask>("run") {
    image(project)
    setCommandLine(listOf("grep", "SandboxDockerExecTask", "/build.gradle.kts"))
}
```
