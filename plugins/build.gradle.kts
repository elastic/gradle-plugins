@file:Suppress("UnstableApiUsage")

subprojects {
    apply(plugin = "java")
    apply(plugin = "jvm-test-suite")
    apply(plugin = "java-gradle-plugin")

    if (! listOf("base", "cli", "docker").contains(project.name)) {
        apply(plugin = "com.gradle.plugin-publish")
    }

    configure<GradlePluginDevelopmentExtension> {
        website.set("https://github.com/elastic/gradle-plugins/blob/main/README.md")
        vcsUrl.set("https://github.com/elastic/gradle-plugins/")
        plugins.all {
            tags.addAll(listOf("elastic"))
        }
    }

    configure<TestingExtension> {
        suites {
            val test by getting(JvmTestSuite::class) {
                useJUnitJupiter()
            }

            val integrationTest by registering(JvmTestSuite::class) {
                dependencies {
                    implementation(project())
                }

                targets {
                    all {
                        testTask.configure {
                            shouldRunAfter(test)
                        }
                    }
                }
            }

            tasks.named("check") {
                dependsOn(integrationTest)
            }
        }
    }

    tasks.withType<Test> {
       maxParallelForks = gradle.startParameter.maxWorkerCount
    }

    dependencies {
        "integrationTestImplementation"(project(":libs:test-utils"))
    }

}