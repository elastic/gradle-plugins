gradlePlugin {
    plugins {
        testSourceSets(java.sourceSets.integrationTest.get())
        create("co.elastic.vault") {
            id = "co.elastic.vault"
            implementationClass = "co.elastic.gradle.vault.VaultPlugin"
            displayName = "Elastic Vault Plugin"
            description = "Plugin to interact with Hashicorp Vault"
        }
    }
}

dependencies {
    implementation(project(":libs:utils"))
    implementation("com.bettercloud:vault-java-driver:5.1.0")
    integrationTestImplementation("com.bettercloud:vault-java-driver:5.1.0")
    integrationTestImplementation("org.testcontainers:vault:1.20.1")
}

