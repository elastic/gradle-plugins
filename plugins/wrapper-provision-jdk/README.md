Docker Wrapper JDK provision Plugin
====================================

About
-----

This plugin alters the default wrapper script after generation to include a snippet that uses `curl` with the 
[adoptium API](https://api.adoptium.net/) to provision and cache a JDK. This makes it possible to run Gradle without a 
JDK to be installed prior to that. The checksum of the downloaded JDK is verified to make sure we're not executing 
untrusted code. 

The `JAVA_HOME_OVERRIDE` environment variable can be used to instruct the wrapper to use an existing JDK. 

Usage
-----
```kotlin
    import co.elastic.gradle.utils.OS.*
    import co.elastic.gradle.utils.Architecture.*
    plugins {
        id("co.elastic.wrapper-provision-jdk")
    }
    tasks.wrapperProvisionJdk {
        javaReleaseName.set("11.0.13+8")
        appleM1URLOverride.set(
            "https://download.bell-sw.com/java/11.0.13+8/bellsoft-jdk11.0.13+8-macos-aarch64.tar.gz"
        )
        checksums.set(
          mapOf(
             LINUX to mapOf(
               X86_64 to "3b1c0c34be4c894e64135a454f2d5aaa4bd10aea04ec2fa0c0efe6bb26528e30",
               AARCH64 to "a77013bff10a5e9c59159231dd5c4bd071fc4c24beed42bd49b82803ba9506ef"
             ),
             DARWIN to mapOf(
               X86_64 to "2b862f97b872e37f8c7ad6d3d30f7d0fcb3f0b951740c8fa142dea702945973c",
               AARCH64 to "7dce00825d5ff0d6f2d39fa1add59ce7f4eefee5b588981b43708d00c43f4f9b"
             )
          )
        ) 
    }
```

Note that a custom download URL can be provided for a JDK for the Apple M1 machines since Adoptium does not support it 
yet for all versions. 

Limitations
-----------

The plugin does not yet offer a convenient way to update the version and matching checksums of the JDKs.
This would technically be possible as the information is available in the Adoptium API.