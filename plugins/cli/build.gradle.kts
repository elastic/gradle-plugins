subprojects {
    tasks.integrationTest {
        // Need to validate these on a per OS and architecture basis
        inputs.properties("OS" to co.elastic.gradle.utils.OS.current())
        inputs.properties("Architecture" to co.elastic.gradle.utils.Architecture.current())
    }
}