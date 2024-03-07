gradlePlugin {
    plugins {
        testSourceSets(java.sourceSets.integrationTest.get())
        create("co.elastic.lifecycle") {
            id = "co.elastic.lifecycle"
            implementationClass = "co.elastic.gradle.lifecycle.LifecyclePlugin"
            displayName = "Elastic Lifecycle"
            description = "Implements the opinionated extend lifecycle"
        }
    }
    plugins {
        testSourceSets(java.sourceSets.integrationTest.get())
        create("co.elastic.lifecycle-multi-arch") {
            id = "co.elastic.lifecycle-multi-arch"
            implementationClass = "co.elastic.gradle.lifecycle.MultiArchLifecyclePlugin"
            displayName = "Elastic Multi Architecture Lifecycle"
            description = "Implements an opinionated lifecycle for multiple archtiectures"
        }
    }
}

dependencies {
    implementation(project(":libs:utils"))
}