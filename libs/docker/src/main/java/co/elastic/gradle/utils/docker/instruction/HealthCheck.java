package co.elastic.gradle.utils.docker.instruction;

import org.gradle.api.tasks.Input;
import org.gradle.api.tasks.Optional;

public class HealthCheck implements ContainerImageBuildInstruction {
    private final String cmd;
    private final String interval;
    private final String timeout;
    private final String startPeriod;
    private final Integer retries;

    public HealthCheck(String cmd, String interval, String timeout, String startPeriod, Integer retries) {
        this.cmd = cmd;
        this.interval = interval;
        this.timeout = timeout;
        this.startPeriod = startPeriod;
        this.retries = retries;
    }

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
