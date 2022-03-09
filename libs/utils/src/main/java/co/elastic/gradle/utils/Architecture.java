package co.elastic.gradle.utils;

import java.util.Map;

public enum Architecture {
    X86_64,
    AARCH64;

    public static Architecture current() {
        String arch = System.getProperty("os.arch").toLowerCase();
        switch (arch) {
            case "amd64":
            case "x86_64":
                return X86_64;
            case "aarch64":
                return AARCH64;
            default:
                throw new IllegalStateException("Unknown co.elastic.gradle.utils.Architecture " + arch);
        }
    }

    public String map(Map<Architecture, String> map) {
        return map.getOrDefault(this, name()).toLowerCase();
    }

    public String dockerName() {
        switch (this) {
            case X86_64:
                return "amd64";
            case AARCH64:
                return "arm64";
            default: throw new IllegalStateException("No docker name for " + this);
        }
    }
}
