package co.elastic.gradle.utils;


import java.util.Map;

public enum OS {
    LINUX,
    DARWIN;


    public static OS current() {
        String osName = System.getProperty("os.name").toLowerCase();
        if (osName.contains("mac os x") || osName.contains("darwin") || osName.contains("osx")) {
            return DARWIN;
        }
        if (osName.contains("linux")) {
            return LINUX;
        }
        throw new IllegalStateException("Unknown OS " + osName);
    }

    public String map(Map<OS, String> map) {
        return map.getOrDefault(this, name()).toLowerCase();
    }
}
