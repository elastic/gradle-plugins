Check in Generated Plugin 
=========================

About
-----

Occasionally one might want to check in a file that is otherwise generated e.g. a JSON API spec generated from the code
or a client library generated form a spec etc. Seeing differences in the generated code might aid in code reviews or 
make them more accessible to folks who don't have the setup to quickly generate them. 

This plugin allows for this setup while also providing a way to check that the checked in code is actually the one that 
should be generated.

It does this by requiring that the generation happens in the build directory or any other location that is ignored in 
source control and then sets up a task to move the result into the location that is checked in. It also sets up a task 
usable in CI to check that the two locations match.

Usage
------

```kotlin
plugins {
   id("co.elastic.check-in-generated")
}

// Any task that generates some files into the build dir
val doGenerate by tasks.registering {
   doLast {
      file("build/bar/bar").mkdirs()
      file("build/foo.txt").writeText("This is FOO")
      file("build/bar/bar/bar.txt").writeText("This is nested BAR")
   }
}

checkInGenerated {
    generatorTask.set(doGenerate)
    map.set(mapOf(
           project.file("build/foo.txt") to project.file("foo.txt"),
           project.file("build/bar") to project.file("bar")
    ))
}
```

To run the generation _and_ copy the files to the checked in location run:
```shell
./gradlew generate
```

To check that the generated files are correct, run (integrate into CI):
```shell
./gradlew verifyGenerated
```

The plugin has protection built in against accidentally calling the equivalent of:
```shell
./gradlew generate verifyGenerated
```
The verification task will always run before the generation.