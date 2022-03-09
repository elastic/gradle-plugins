package co.elastic.gradle.utils;


public enum OS {
    LINUX,
    WINDOWS,
    DARWIN;


    public static OS current() {
        String osName = System.getProperty("os.name").toLowerCase();
        if (osName.contains("windows")) {
            return WINDOWS;
        }
        if (osName.contains("mac os x") || osName.contains("darwin") || osName.contains("osx")) {
            return DARWIN;
        }
        if (osName.contains("linux")) {
            return LINUX;
        }
        throw new IllegalStateException("Unknown OS " + osName);
    }
}
