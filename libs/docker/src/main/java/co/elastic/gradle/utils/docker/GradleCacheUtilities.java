package co.elastic.gradle.utils.docker;

import org.gradle.api.GradleException;

abstract public class GradleCacheUtilities {

    public static void assertOutputSize(String taskPath, long outputSize, Long maxSizeMB) {
        long maxSize = maxSizeMB * 1024 * 1024;
        if (outputSize > maxSize) {
            throw new GradleException("Task " + taskPath + " output (" + (outputSize / (1024 * 1024)) +
                    "Mb) is greater than the current limit of "+maxSizeMB+"Mb.");
        }
    }

}
