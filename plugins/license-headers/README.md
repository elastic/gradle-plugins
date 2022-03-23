License Headers Plugin
======================

About
-----

This plugin enforces that a specific header is present in each file. The contents of the header to be checked is read 
from a configurable file.

The plugin also provides the `fixLicenseHeaders` task to add the header when missing automatically. 

Usage
-----

The default location for the header file that has the expected header is `src/header.txt`.

```kotlin
plugins {
    id("co.elastic.license-headers")
}
licenseHeaders {
    check(fileTree(projectDir)) {
        exclude("build.gradle.kts")
    }
}
```

Multiple `check` configurations are also supported: 
```kotlin
plugins {
    id("co.elastic.license-headers")
}
licenseHeaders {
    check(fileTree("src/main"))
    check(fileTree("src/generated")) {
        headerFile.set(file("header_generated.txt"))
    }
}
```

The plugin works with the [Lifecycle](../lifecycle/README.md) plugin and registeres it's tasks to `check` and `autoFix`.

You can run `./gradlew checkLicenseHeaders` to check the headers and `gradlew fixLicenseHeaders` to add missing headers. 

## exclude()

Can take a variable argument or list of glob patterns to exclude from being scanned from the file tree.

## include() 

Can take a variable argument list of glob patterns to include into being scanned. 

## headerFile 

Property used to configure the location of the header file. `src/header.txt` by default. 

