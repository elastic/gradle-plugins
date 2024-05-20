Docker Base Image Build Plugin
==============================

About
-----

The scope of the plugin is to generate low level "base" container images that implements the OS level support required
for an application to run. It provides a convenience wrapper around building docker images with `Dockerfile`s. The
plugin generates such a file and calls docker commands to build and export an image with caching enabled, in a slightly
opinionated way and with support for additional features as compared to plain Dockerfiles and with deep 
OS level integration.

The configuration of the resulting image is specified using a custom DSL inside the build script. This approach allows
us to provide additional features around installing packages, creating users and managing repositories being
used to install packages.

Most importantly the plugin offers a way to do OS package installation in a way similar to nodejs using a lockfile of
the package versions that are to be installed, as well as a way to pick up updates, including updates to the source
image. All in all this provides a very powerful way of making docker image builds deterministic and controlling when the
updates are picked up as opposed on relying on re-builds to keep up-to date and risking picking up a package upgrade at
a bad time. We consider this approach provides the best of both words: it keeps builds reproducible, deterministic and
allows one control over what is included in the image while still being able to keep up-to-date to make sure the
resulting image don't have any security vulnerabilities.

Note that not all Dockerfile instructions are present in the DSL. That's because the plugin was meant to be used in
conjunction with the [component image build plugin](../component-image/README.md) that makes the instruction set
complete and more (Support for manifest lists, security scanning etc).

Limitations
-----------

Multi project builds depend on the import task and the tag it creates, so multiple concurrent builds e.g. building
different branches might result in crosstalk.

A generic Artifactory repository is required to make the plugin work. Public mirrors typically remove package
versions eagerly as newer versions become available, so there's no guarantee that the versions in the lockfile are still
installable. On the bright side this setup also shields the builds from failures or connectivity issues with the public
infrastructure.

Supported Base Images
---------------------

Since the plugin interacts with the package manager inside the image the following base images are supported. The OS specificed
by using the appropriate DSL method:
 - use `fromUbuntu(...)` for Ubuntu 
 - use `fromWolfi(...)` for Chainguard Wolfi (uses apk, alpine images might work but are not tested)
 - use `fromCentos()`for CentOS - deprecated and untested 

Usage
-----

### Building a docker image

```kotlin
import java.net.URL

plugins {
    id("co.elastic.docker-base")
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
dockerBaseImage {
    dockerTagLocalPrefix.set("gradle-test-local")                // configures how the image is imported to the local daemon
    dockerTagPrefix.set("docker.elastic.co/employees/ghHandle")  // configures where the image is pushed 
    // Using a mirror is mandatory as older version of pacakges are removed from public mirrors
    osPackageRepository.set(URL("https://${creds["username"]}:${creds["plaintext"]}@artifactory.elastic.dev/artifactory/gradle-plugins-os-packages"))
    fromUbuntu("ubuntu", "20.04")           // Specify the source image, hinting at the distribution 
    install("patch")                        // Install the patch utility using the version from the lockfile  
    copySpec("1234:1234") {                 // Include files and set their ownership 
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
    createUser("foobar", 1234, "foobar", 1234)  // generate commands to create a specific user 
    run(
        "ls -Ral /home",
        "echo \"This plugin rocks on $architecture and ephemeral files are available at $dockerEphemeral!\" > /home/foobar/bar.txt"
    )   // Run commands and create a single layer from all
    run(
        listOf(
            "touch /home/foobar/run.listOf.1",
            "touch /home/foobar/run.listOf.2",
            "chmod -R 777 /home"
        )
    )
    setUser("foobar")                    // configure the default user of the image
    run("whoami > /home/foobar/whoami")
}
```

This example also shows how to integrate this plugin with the [vault Gradle plugin](../../vault/README.md). Each
instruction creates a layer in the resulting docker image.

The name of the image matches the current `project.name`, and the tag is set from
`project.version` and current Architecture (e.g. `amd64` or `arm64`) by convention and is not configurable.

Before building the image for the first time, or to update the version of dependencies and base image that are to be
picked up:

```shell
./gradlew dockerBaseImageLockfile 
```

This will create a lock-file that that is meant to be checked in and offers a way to control when the base image is
updated. Running the command again will always get the latest repo digests pointed to by the tag. This approach works
better with build avoidance and is more predictable as one can control when updates are picked up as opposed to maybe
getting them right before or while preparing for a release. For convenience and to make sure it's not forgotten, this
functionality can be used in automation that will re-generate the lock-file and open a PR with the result.

As part of generating the lockfile, OS packages used to build the image will also be uploaded to the configured 
repository. When building the image these are pulled using a Gradle Configuration, so they will get cached locally 
and only downloaded once, making it much faster to iterate on image builds.

When using Docker Desktop or having emulation configured by other means, one can generate the 
lockfile for all architectures in one go with:
```shell
./gradlew dockerBaseImageLockfileAllWithEmulation
```

One can then build and optionally push the resulting image:

```shell
./gradlew dockerBaseImageBuild dockerBaseImagePush
```

Docker needs to be authenticated with the right permissions for the push to work.

Note that building the image only stores an archive of it, to also have it available in the local daemon one has to run:

```shell
./gradlew dockerLocalImport
```

See the source of [BaseImageExtension](src/main/java/co/elastic/gradle/dockerbase/BaseImageExtension.java)  
for all configuration options of the image.

### Configuring additional package repositories 

When building the docker images all the default package repositories are replaced with the local repository from Gradle
created based on the lockfile.  This repository has content from the lockfile and nothing else guaranteeing that the 
image build will be hermetic. When using custom repositories, these need to be configured only 
when generating the lockfile, as they might fail and are not needed when the image is built, so
commands that configure an additional repository have to be wrapped with special syntax:

```kotlin
dockerBaseImage {
    fromCentos("centos", "7")
    repoConfig("yum -y install epel-release")
    install("jq")
    run("jq --version")
}
```

This example runs a `yum` command to install the EPEL repository that hosts the `jq` tool. 

**NOTE**: Because the repo config instructions are only taken into account when the lockfile is generated, none of the 
effects will be visible in the built image. In this case the resulting image _will_ have `jq` installed, but will not 
have EPEL configured.

### Cleaning up

Running

```shell
./gradlew clean
```

Will also remove any image from the daemon created or imported by this plugin.

### copySpec([owner UID:owner GUID])

Add files to the container. Similar to a Gradle [Copy] task. Check out the Gradle docs
on [working with files](https://docs.gradle.org/current/userguide/working_with_files.html)
for more information on how to use it.

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
dockerBaseImage {
    copySpec {
        from(archive)
        from(custom)
        into("home")
    }
    run("ls /home/my.zip", "ls /home/some_output.txt")
}
```
That's all, Gradle creates a task dependency based on the copySpec for you.  

### Multi project support

Gradle projects can define docker images that build on each-other. This creates a bidirectional dependency causing
down-stream projects to re-build when the image changes and upstream plugins to build if they have not already.

#### `settings.gradle.kts`

```kotlin
include("s1")
include("s2")
include("s3")
```

#### Root build.gradle.kts

```kotlin
import java.net.URL
import co.elastic.gradle.dockerbase.BaseImageExtension

evaluationDependsOnChildren()

plugins {
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

subprojects {
    print(project.name)
    configure<BaseImageExtension> {
        osPackageRepository.set(URL("https://${creds["username"]}:${creds["plaintext"]}@artifactory.elastic.dev/artifactory/gradle-plugins-os-packages"))
    }
}
```

#### s1/build.gradle.kts

```kotlin
plugins {
    id("co.elastic.docker-base")
}
dockerBaseImage {
    fromUbuntu("ubuntu", "20.04")
    install("patch")
}
``` 

#### s2/build.gradle.kts

```kotlin
plugins {
    id("co.elastic.docker-base")
}
dockerBaseImage {
    from(project(":s1"))
    run("patch --version")
    install("jq")
}
```

#### s3/build.gradle.kts

```kotlin
plugins {
    id("co.elastic.docker-base")
}
dockerBaseImage {
    from(project(":s2"))
    run("jq --version")
}
```

### Limiting the maximum size of the image

```kotlin
dockerBaseImage {
    maxOutputSizeMB.set(1024)
}
```

Docker images tend to be bigger than the default max allowed size, so Gradle will silently not cache these. To make sure
an image will be cached its good practice to set a maximum size limit to the resulting compressed archive of the image
and make sure it's bellow the max allowed cache artefact size. There's some metadata involved too so make sure to leave
a buffer.

### Building images for multiple platforms

The plugin doesn't support any type of emulation and will only build images matching the platform (CPU architecture)
it's running on. In order to create images for multiple architectures one needs to configure the CI jobs to run the
build and push targets. Since the architecture name is included in the tag these images won't overwrite each other.

By default, an image build will be attempted on any platform. This will fail if the source image or some of the inputs
are not available, e.g. when using older images that only have an `amd64` variant.   
One can be explicit about the architectures the image needs to be build for:

```kotlin
import co.elastic.gradle.utils.Architecture.*

dockerBaseImage {
    platforms.add(X86_64)
}

```

When configured like this, all the tasks will be `SKIPPED` when running on a platform that is not explicitly added. E.g.
with the configuration above an image will be build when running on an `x86_64` CPU, but will be skipped when the build
is running on `aarch64`.

### Integration with the Docker Sandbox Plugin

The plugin can be used in conjunction with the [sandbox plugin](../../sandbox/README.md) to run a container with the
image that was just built:

```kotlin
import java.net.URL
import co.elastic.gradle.sandbox.SandboxDockerExecTask

plugins {
    id("co.elastic.docker-base")
    id("co.elastic.vault")
    id("co.elastic.sandbox")
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
dockerBaseImage {
    osPackageRepository.set(URL("https://${creds["username"]}:${creds["plaintext"]}@artifactory.elastic.dev/artifactory/gradle-plugins-os-packages"))
    fromUbuntu("ubuntu", "20.04")
    copySpec {
        from(projectDir) {
            include("build.gradle.kts")
        }
    }
}
tasks.register<SandboxDockerExecTask>("test") {
    image(project)
    setCommandLine(listOf("grep", "SandboxDockerExecTask", "/build.gradle.kts"))
}
```

This is a powerful combination that allows one to build a custom image and then use it to generate a build artefact
using that image all with build avoidance end-to-end (e.g. the image is only rebuilt if something actually changed). 