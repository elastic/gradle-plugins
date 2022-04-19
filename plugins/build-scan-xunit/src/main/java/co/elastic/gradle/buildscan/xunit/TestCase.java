package co.elastic.gradle.buildscan.xunit;

import java.time.LocalDateTime;
import java.util.Optional;

public record TestCase(
        String name,
        String className,
        Double time,
        TestCaseStatus status
) {
    public LocalDateTime endTime(LocalDateTime suiteStartTime) {
        return suiteStartTime
                .plusSeconds(Optional.of(time).orElse(0.0).longValue() * 1000 * 1000);
    }
}
