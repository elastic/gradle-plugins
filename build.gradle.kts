plugins {
    id("com.gradle.plugin-publish").version("1.2.1").apply(false)
}

allprojects {
    repositories {
        mavenCentral()
    }

    version = "0.0.1"
    group = "co.elastic.gradle"

    if (extensions.findByType(JavaPluginExtension::class) != null) {
        configure<JavaPluginExtension> {
            toolchain {
                languageVersion.set(JavaLanguageVersion.of("17"))
            }
        }
    }
}