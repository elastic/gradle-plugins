package co.elastic.gradle.buildscan.xunit;

public record TestCaseSkipped(String message) implements TestCaseStatus {
}
