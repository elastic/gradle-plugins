Gradle Lifecycle Plugin
=======================

About 
-----

Keeping similar meta targets consistent against projects is a great enabler for large teams. It doesn't matter which 
project it is or if you're building it the first time or the thousands time, the same commands can be used to assemble 
artefacts, run the tests etc. 

The [Gradle Base Plugin](https://docs.gradle.org/current/userguide/base_plugin.html#sec:base_tasks) carters for this need
for the most generic such targets. This plugin extends this with a set of more opinionated targets, as well as adds 
optional support for building across multiple architectures (x86, arm64).  

Usage
-----

```kotlin
plugins {
    id("co.elastic.lifecycle")
}
```

Will create additional tasks such as `resolveAllDependencies`, `preCommit`, `syncBinDir`. 

```kotlin
plugins {
    id("co.elastic.lifecycle-multi-arch")
}
```

Will create architecture aware tasks for specific, existing lifecycle tasks.  

Run 
```
./gradlew tasks
```
To see all the created tasks and their descriptions. 


How multi architecture support works 
------------------------------------

The `co.elastic.lifecycle-multi-arch` splits the build into 4 parts: 
- anything that needs to be done on a specific platform, using the `ForPlatfrom` suffix 
- anything that can be done on any platform (the platform doesn't matter), using the `PlatformIndependent` suffix
- a step that combines everything together that can itself run on any platform, using the `CombinePlatform` suffix. 
  This assumes that `ForPlatform` has already run for each supported platform and `PlatfromIndependent` ran at least once
  and their artefacts are published and available.
- The regular lifecycle tasks, available for convenience when developing locally.

A typical CI implementation for e.g. amd64 and arm64 will run as follows:
```
(arm64 worker) ./gradlew publishForPlatform publishPlatformIndent  ----\  
                                                                        \
                                                                         \
                                                                          >--- ./gradlew publishCombinePlatform
                                                                         /
                                                                        / 
(amd64 worker) ./gradlew publishForPlatform publishPlatformIndent  ----/
```

Tasks are generated with these suffixes for `build`, `check`, `publish` and `assemble`.

The plugin automatically adds all the suffixed versions of a task as dependencies to the non suffixed version, e.g.
`assemble` will depend on `assembleForPlatform`, `assemblePlatformIndent` and `assembleCombinePlatform`. Authors and 
build script authors should thus crete a way to inform the build that it's ok to consider only the local platform for a 
specific change and create artefacts that support it. When running in CI or as part of a more elaborate development 
setup (e.g. one with remove machine or emulation) it should still be possible to run the suffixed version of the tasks.

Using the synced binary directories
------------------------------------

Run Gradle to create the bin dir and add it to your path:
```shell
./gradlew syncBinDir  # make sure the bin dir is up to date
export PATH=$PWD/.gradle/bin:$PATH # Add the bin dir to the path
some-gradle-provisioned-tool --help
```

Working without an internet connection 
--------------------------------------

The `resolveAllDependencies` makes sure anything you might need is available locally as long as any network resource is 
accessed through a [Gradle Depdency Configuration](https://docs.gradle.org/current/userguide/declaring_dependencies.html#sec:what-are-dependency-configurations).

```shell
./gradlew resolveAllDependencies # run once to download everything you might need 
./gradlew --offline build 
```
