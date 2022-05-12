val JAVA_MIN_SUPPORTED = JavaVersion.VERSION_11
val JAVA_LANGUAGE_LEVEL = JavaVersion.VERSION_17

if (JavaVersion.current() < JAVA_LANGUAGE_LEVEL) {
    throw GradleException("Minimum Java version to build this project is $JAVA_LANGUAGE_LEVEL")
}

allprojects {
    repositories {
        mavenCentral()
    }

    if (extensions.findByType(JavaPluginExtension::class) != null) {
        configure<JavaPluginExtension> {
            sourceCompatibility = JAVA_LANGUAGE_LEVEL
            targetCompatibility = JAVA_MIN_SUPPORTED
            toolchain {
                languageVersion.set(JavaLanguageVersion.of(JAVA_LANGUAGE_LEVEL.majorVersion))
            }
        }
    }
}