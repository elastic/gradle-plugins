package co.elastic.gradle.buildscan.xunit;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.function.Supplier;

public record TestSuite (
        String name,
        Integer errors,
        Integer failures,
        Integer skipped,
        LocalDateTime timestamp,
        Double time,
        List<TestCase> tests
) {
    public LocalDateTime startedTime(Supplier<LocalDateTime> defaultStartTime) {
        return Optional.ofNullable(timestamp).orElseGet(defaultStartTime);
    }

    public LocalDateTime endTime(Supplier<LocalDateTime> defaultStartTime) {
        Double convertedTime = Optional.of(this.time).orElse(0.0) * 1000 * 1000;
        return startedTime(defaultStartTime)
                .plusSeconds(convertedTime.longValue());
    }
}
