package co.elastic.gradle.buildscan.xunit;

public record TestCaseError(
        String message,
        String type,
        String description
) implements TestCaseStatus {

}
