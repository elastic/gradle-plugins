package co.elastic.gradle.utils.docker.instruction;

import org.gradle.api.tasks.Input;
import org.gradle.api.tasks.Optional;

public record HealthCheck(String cmd, String interval, String timeout,
                          String startPeriod,
                          Integer retries) implements ContainerImageBuildInstruction {

    @Input
    public String getCmd() {
        return cmd;
    }

    @Input
    @Optional
    public String getInterval() {
        return interval;
    }

    @Input
    @Optional
    public String getTimeout() {
        return timeout;
    }

    @Input
    @Optional
    public String getStartPeriod() {
        return startPeriod;
    }

    @Input
    @Optional
    public Integer getRetries() {
        return retries;
    }
}
