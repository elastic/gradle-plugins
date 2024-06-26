@file:Suppress("UnstableApiUsage")

subprojects {
    apply(plugin = "java")
    apply(plugin = "jvm-test-suite")

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