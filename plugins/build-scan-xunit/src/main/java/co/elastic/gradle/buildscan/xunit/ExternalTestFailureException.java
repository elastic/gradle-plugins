package co.elastic.gradle.buildscan.xunit;

public class ExternalTestFailureException extends RuntimeException {
    public ExternalTestFailureException(String message) {
        super(message);
    }
}
