package co.elastic.gradle.buildscan.xunit;

public record TestCaseFailure(
        String message,
        String type,
        String description
) implements TestCaseStatus {
}
