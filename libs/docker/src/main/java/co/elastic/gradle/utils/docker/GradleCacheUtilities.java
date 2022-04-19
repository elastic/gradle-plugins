package co.elastic.gradle.utils.docker;

import org.gradle.api.GradleException;

abstract public class GradleCacheUtilities {

    static public final long MAX_CACHE_ARTIFACT_SIZE_MB = 1950;

    public static void assertOutputSize(String taskPath, long outputSize, Long maxSizeMB) {
        long maxSize = maxSizeMB * 1024 * 1024;
        if (outputSize > maxSize) {
            throw new GradleException("Task " + taskPath + " output (" + (outputSize / (1024 * 1024)) +
                    "Mb) is greater than the current limit of "+maxSizeMB+"Mb.");
        }
    }

}
