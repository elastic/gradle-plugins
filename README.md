About
=====

This repository hosts a collection of Gradle plugins meant to extend the capabilities of Gradle and 
implement [hermetic builds](https://sre.google/sre-book/release-engineering/#hermetic-builds-nqslhnid).

The plugins are likely to work best if Gradle is the build system of choice, but can be used integrated 
with other build systems too, e.g. just to build docker images.

Gradle plugins 
==============

- [co.elastic.build-scan.xunit](plugins/build-scan-xunit/README.md): import xunit files into build scan test results
- [co.elastic.check-in-generated](plugins/check-in-generated/README.md): Support for keeping checked in generated code up to date
- [co.elastic.cli](plugins/cli/README.md): Efficiently provision and run various cli tools
- [co.elastic.docker-base](plugins/docker/base-image/README.md): Building reproducible Docker base images
- [co.elastic.docker-component](plugins/docker/component-image/README.md): Building multi-platform Docker images for applications
- [co.elastic.license-headers](plugins/license-headers/README.md): Enforce license headers in source files
- [co.elastic.lifecycle](plugins/lifecycle/README.md): extended lifecycle tasks
- [co.elastic.lifecycle-multi-arch](plugins/lifecycle/README.md): support for building on multiple architectures
- [co.elastic.gradle.sandbox](plugins/sandbox/README.md): Run commands with build avoidance in isolation
- [co.elastic.gradle.vault](plugins/vault/README.md): Integration with [vault](https://www.vaultproject.io/)
- [co.elastic.wrapper-provision-jdk](plugins/wrapper-provision-jdk/README.md): Extend the wrapper script to include provisioning of the JDK

Design goals
=============

Make use of modern Gradle features and keep up to date with Gradle 
------------------------------------------------------------------

Recent versions of Gradle offer a rich set of features for plugin and build script authors alike. These centre around
performance of iterating and overall build time reduction. Some examples:
[lazy configuration](https://docs.gradle.org/current/userguide/lazy_configuration.html), 
[build cache](https://docs.gradle.org/current/userguide/build_cache.html). Unfortunately there are many Gradle plugins 
that were created before these were available and don't take advantage of them.

The plugins defined here must make use of these facilities to enhance the performance of builds. Custom tasks need to 
define all inputs and outputs and must be marked as cachable where appropriate. The plugins must make use of lazy 
configuration and otherwise make sure that the configuration phase is fast. 

By doing so we can assure that quick tasks like `tasks` or `help` run nearly instantly, and anything else only really 
runs if something changed since the last invocation.


Avoid creating requirements for the machine running the build 
--------------------------------------------------------------

The plugins should not make any assumptions about the machine running the build. Linux and OSX must be supported.
Where practical plugins should not break when applied to Windows and should be specific where Windows is not supported, 
or degrade gracefully. Windows support is on a best-effort basis and not something we continuously test. This being the 
JVM most things should work, but there are no guarantees.

Assumptions about the machine include but are not limited to the availability of software. A JVM will be available as 
that's running Gradle, but nothing else should be taken for granted. As a convention and enabler plugins can assume that 
docker will be available and use it, or otherwise provision required tools and libraries.

By doing so, it will be easy to change the versions of these requirements without changes having to be matched by developers 
and CI infrastructure, and it will be easy to reproduce failures from CI locally. 

Use Gradle Configurations for any dependent artefacts
-----------------------------------------------------

Plugins access any dependencies using the built-in mechanism of  [dependency configurations](https://docs.gradle.org/current/userguide/declaring_dependencies.html#sec:what-are-dependency-configurations)
instead of implementing direct downloads. Doing so has the advantage that these will be cached in the 
[dependency cache](https://docs.gradle.org/current/userguide/dependency_resolution.html#sec:dependency_cache) so it's easy 
to bake them in CI workers and users running locally will only have to download once.
In addition with [dependency verificatio](https://docs.gradle.org/current/userguide/dependency_verification.html) enabled
the checksum of these dependencies will also be verified.

Gradle doesn't have a generic way to configure dependencies to generic URLS, but Ivy reposirory definitions are generic 
enough to support it.

Limitations
===========

- No support for Windows, Linux and Mac on X86_64 and ARM64 are supported
- We made and effort to avoid it but some plugins still use internal Gradle APIs as there was no alternative
- Only tested against the latest Gradle version (this might change)
