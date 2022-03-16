gradlePlugin {
    plugins {
        testSourceSets(java.sourceSets.integrationTest.get())
        create("co.elastic.lifecycle") {
            id = "co.elastic.lifecycle"
            implementationClass = "co.elastic.gradle.lifecycle.LifecyclePlugin"
        }
    }
    plugins {
        testSourceSets(java.sourceSets.integrationTest.get())
        create("co.elastic.lifecycle-multi-arch") {
            id = "co.elastic.lifecycle-multi-arch"
            implementationClass = "co.elastic.gradle.lifecycle.MultiArchLifecyclePlugin"
        }
    }
}

dependencies {
    implementation(project(":libs:utils"))
}