Gradle Sandbox Plugin
=====================

About
-----

This plugin provides a way to run a command line with safe build avoidance and caching enabled. All files red and
written as well as any commands ran need to be explicitly declared using the provided methods on the task. The 
implementation does not move any data arround. 

The tasks will create a temporary directory structure with all the directories from read at a randomised location and
hard-link the original files there. This is similar to the result of `cp -l`. This makes it seam to the command being
run that it's running in the project directory, but accessing any file that is not specifically declared with `reads`
will result in a file not found error.

The docker variant offers stronger sandbox guarantees by using docker containers. The tag of the image being used is
tracked as input, so anything inside the image is safe in regard to build avoidance. Note that the input is the tag
itself, not the contents of the image so the task will not be re-run if the underlying image changes without the tag.
It's recommended to use a message digest as part of the image identification to prevent over-caching.

The plugin will create a `resolveSandboxDockerDependencies` task to try to pull any docker image being used multiple times 
before creating the container. This can also be used to warm up docker or bake images into CI workers to prevent relying 
on the network when building.

Limitations
-----------

- `SandboxDockerExec` task doesn't use any OS level namespacing, as such it allows files to be accessed by absolute path
  and these will not be considered inputs. This is true for executables too, they can run without being declared if called 
  with an absolute path. Consider using the docker variant if stringer sandbox guarantees are
  required. 
- The task does not account for any side effects of running the command, other than files or directories being created
  that must be declared with `writes`. Anything declared with `writes` will be available even if the task
  is `FROM_CACHE`, but any side effects will not. Make sure not to relay on command lines with side effects, or not to
  rely on those side effects.

Usage
-----

### SandboxExecTask

```kotlin
import co.elastic.gradle.sandbox.SandboxExecTask

plugins {
    id("co.elastic.sandbox")
}
tasks.register<SandboxExecTask>("test") {
    setWorkingDir("samples")
    setCommandLine(listOf("../scripts/test.sh", "arg1", "arg2"))
    reads(file("scripts/test.sh"))
    runsSystemBinary("mkdir")
    runsSystemBinary("env")
    runs(file("/usr/bin/find"))
    runsSystemBinary("sed")
    reads(file("samples/file1"))
    reads(fileTree("samples/dir"))
    writes(file("build/script_out/output_file"))
    writes(fileTree("build/script_out_dir"))
    environment("ENV_VAR1", "value1")
    environment(mapOf("ENV_VAR2" to "value2"))
}
```

### SandboxDockerExecTask

```kotlin
import co.elastic.gradle.sandbox.SandboxDockerExecTask

plugins {
    id("co.elastic.sandbox")
}
tasks.register<SandboxDockerExecTask>("test") {
    image("ubuntu:20.04@sha256:8ae9bafbb64f63a50caab98fd3a5e37b3eb837a3e0780b78e5218e63193961f9")
    setWorkingDir("samples")
    setCommandLine(listOf("../scripts/test.sh", "arg1", "arg2"))
    reads(file("scripts/test.sh"))
    reads(file("samples/file1"))
    reads(fileTree("samples/dir"))
    writes(file("build/script_out/output_file"))
    writes(fileTree("build/script_out_dir"))
    environment("ENV_VAR1", "value1")
    environment(mapOf("ENV_VAR2" to "value2"))
}
```

### Retrying commands

```kotlin
import co.elastic.gradle.sandbox.SandboxExecTask

plugins {
    id("co.elastic.sandbox")
}
tasks.register<SandboxExecTask>("test") {
    setCommandLine(listOf("./scripts/test.sh", "arg1", "arg2"))
    reads(file("scripts/test.sh"))
    maxTries(3)
}
```

Using lots of re-tries in builds can get sloppy, slow and hard to track, but re-trying a single command is still better 
than re-running the entire build. The task also makes available the `GRADLE_SANDBOX_TRY_NR` environmental variable in 
case the command wants to know about it being retried. 

### Dealing with large file trees

The sandbox tasks create links to avoid moving data around, but when dealing with large trees there can still have a 
significant performance penalty as Gradle has to traverse the tree multiple times and compute checksums. 
Node modules are notorious for this issue as they can create millions of small files that are slow to process.  
Luckily in some cases there's a single file that has metadata about what's in the large tree, like `package.json` for 
node. For these cases, `reads` supports passing in a `Map<File, FileCollection>` for example:   
```kotlin
   reads(mapOf(file("package.json" to fileTree("node_install"))))
```
Both the keys and the values are made available to te sandbox but, but only the keys are considered as input. 


### Multi project support

The sandbox tasks do all path computations relative to the root project directory of a multi project build.
That makes it possible to pass in files of other projects to `reads` and access them using a relative path 
following the relationship of the projects. For example if `p1` and  `p2` are siblings one can:
```kotlin
 reads(project(":subprojects:p2").file("some_file"))
```
and then access the file as `../p2/some_file` in the sandbox.

Note that while possible, it's not recommended having inner dependencies between projects at the file level. Consider 
implementing better encapsulation and using project dependencies or at least task dependencies instead. 

Methods Specific to SandboxExecTask
-----------------------------------

### runsSystemBinary(String name)

Configures a system binary that is used by the script. The sandbox task creates a directory with symlinks to specific 
system binaries and files passed to `runs` and configures that as a path, so while system tools can be used with an absolute
path, they will fail with a command not found error unless specified.

System binaries are specified by name only, the file is resolved from `/bin` or `/usr/bin`. The contents of the file will
not be considered an input to the task, system binaries (e.g. `cat`, `grep`) are considered stable and generic enought 
that different versions of the tools should not influence the behavior of the task significantly. This makes the cache 
more portable across machines.     

### runs(File command)

Specify a file that will be made accessible to the sandbox and added to the `PATH`. Note that this means that the command 
will be able to be called directly, but this does not make its file accessible the same way `reads` does. 
For example `runs(file(".gradle/bin/node"))` means that a script can call `node` but it won't be added to 
`.gradle/bin` inside the sandbox. 

The file passed as an argument is considered an input to the task, so the task will re-run if the contents of the file 
changes.  

Methods Specific to SandboxDockerExecTask
-----------------------------------------

### image(String tag)

Use a docker image from a registry. It's recommended to include the digest too.  

### image(ContainerImageProviderTask task)

Use a docker image from built by a task locally that implements the `ContainerImageProviderTask` interface.
A dependency on this task is automatically added.

### image(Project project)

Us a docker image built by a different gradle subproject. The subproject must have a single task that implements the 
`ContainerImageProviderTask` interface.

Methods available on both tasks
-------------------------------

### setWorkingDir(String relativePath)

Set the working directory, relative to the sandbox. 

### setCommandLine(List commandLine)

Configures the command line to run in the sandbox.

### reads(File|FileCollection location)

Adds a file or a file collection (e.g. created with `files` or `fileTree`) as input to the task and makes them available
at the same relative paths inside the sandbox.  

### writes(File|FileTree location)

Adds a file or file tree (e.g. created with `fileTree`) as outputs of the task and makes these available in the project 
directory to create the illusion that no sandbox was used. 

### environment(String key, String value) or environment(Map env)

Configure environmental variables to be passed to the sandbox. Existing environmental variables are not passed in.

### maxTries(int number)

Configures how many times the command is to be retried.