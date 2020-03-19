package co.elastic.cloud.gradle.testing.buildscan;

public class ExternalTestFailureException extends RuntimeException {
    public ExternalTestFailureException(String message) {
        super(message);
    }
}
