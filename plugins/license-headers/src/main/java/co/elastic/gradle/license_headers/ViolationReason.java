package co.elastic.gradle.license_headers;

public record ViolationReason(String reason, Type type) {

    public enum Type {
        SHORT_FILE,
        LINE_MISS_MATCH,
        MISSING_HEADER;

    }
}
