package co.elastic.gradle.buildscan.xunit;

public record TestCaseSuccess(String stdout, String stderr) implements TestCaseStatus {
}
