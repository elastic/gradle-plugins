package co.elastic.gradle.dockerbase.lockfile;

import co.elastic.gradle.dockerbase.OSDistribution;
import com.fasterxml.jackson.annotation.JsonCreator;
import org.gradle.api.tasks.Input;

import java.io.Serializable;

public record UnchangingPackage(
        String name,
        String version,
        String release,
        String architecture
) implements Serializable {

    @JsonCreator
    public UnchangingPackage {
    }

    @Input
    public String getName() {
        return name;
    }

    @Input
    public String getVersion() {
        return version;
    }

    @Input
    public String getRelease() {
        return release;
    }

    @Input
    public String getArchitecture() {
        return architecture;
    }

    public String getPackageName(OSDistribution distribution) {
        return switch (distribution) {
            case CENTOS -> String.format("%s-%s-%s.%s", name, version, release, architecture);
            case UBUNTU, DEBIAN -> String.format(
                    "%s=%s%s",
                    name,
                    version,
                    release !=null && !release.equals("") ? "-" + release : ""
            );
        };
    }
}
