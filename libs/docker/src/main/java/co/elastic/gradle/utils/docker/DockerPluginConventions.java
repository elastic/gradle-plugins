/*
 *
 *  * ELASTICSEARCH CONFIDENTIAL
 *  * __________________
 *  *
 *  *  Copyright Elasticsearch B.V. All rights reserved.
 *  *
 *  * NOTICE:  All information contained herein is, and remains
 *  * the property of Elasticsearch B.V. and its suppliers, if any.
 *  * The intellectual and technical concepts contained herein
 *  * are proprietary to Elasticsearch B.V. and its suppliers and
 *  * may be covered by U.S. and Foreign Patents, patents in
 *  * process, and are protected by trade secret or copyright
 *  * law.  Dissemination of this information or reproduction of
 *  * this material is strictly forbidden unless prior written
 *  * permission is obtained from Elasticsearch B.V.
 *
 */

package co.elastic.gradle.utils.docker;

import org.gradle.api.Task;
import org.gradle.api.file.DuplicatesStrategy;
import org.gradle.api.file.FileTree;
import org.gradle.api.internal.file.copy.CopySpecInternal;
import org.gradle.api.internal.file.copy.CopySpecResolver;
import org.gradle.api.tasks.PathSensitivity;
import org.gradle.api.tasks.TaskProvider;

import java.util.concurrent.Callable;

public class DockerPluginConventions {

    public static CopySpecInternal.CopySpecListener mapCopySpecToTaskInputs(TaskProvider<? extends Task> taskProvider) {
        return (path, spec) -> {
            doMapCopySpecToTaskInputs(path, spec, taskProvider.get());
        };
    }

    public static CopySpecInternal.CopySpecListener mapCopySpecToTaskInputs(Task task) {
        return (path, spec) -> {
            doMapCopySpecToTaskInputs(path, spec, task);
        };
    }

    private static void doMapCopySpecToTaskInputs(CopySpecInternal.CopySpecAddress path, CopySpecInternal spec, Task task) {
        // Create runtime properties to track the CopySpec as input thus invalidating the cache if any of the input files change
        // unfortunetly there's no public API so we use some internals here, the code is inspired from Gradles
        // AbstractCopyTask
        if (spec.hasCustomActions()) {
            task.getLogger().warn("Custom actions on copy specs (e.g. `rename`) are not taken into account when computing the cache key");
        }
        StringBuilder specPropertyNameBuilder = new StringBuilder("copySpec");
        CopySpecResolver parentResolver = path.unroll(specPropertyNameBuilder);
        CopySpecResolver resolver = spec.buildResolverRelativeToParent(parentResolver);
        String specPropertyName = specPropertyNameBuilder.toString();

        task.getInputs().files((Callable<FileTree>) resolver::getSource)
                .withPropertyName(specPropertyName)
                // This is the source path of the file, it's being relocated in the image, so we don't care where
                // it's coming form, we just care about the file contents.
                .withPathSensitivity(PathSensitivity.NONE);

        task.getInputs().property(specPropertyName + ".destPath", (Callable<String>) () -> resolver.getDestPath().getPathString());
        task.getInputs().property(specPropertyName + ".caseSensitive", (Callable<Boolean>) spec::isCaseSensitive);
        task.getInputs().property(specPropertyName + ".includeEmptyDirs", (Callable<Boolean>) spec::getIncludeEmptyDirs);
        task.getInputs().property(specPropertyName + ".duplicatesStrategy", (Callable<DuplicatesStrategy>) spec::getDuplicatesStrategy);
        task.getInputs().property(specPropertyName + ".dirMode", (Callable<Integer>) spec::getDirMode)
                .optional(true);
        task.getInputs().property(specPropertyName + ".fileMode", (Callable<Integer>) spec::getFileMode)
                .optional(true);
        task.getInputs().property(specPropertyName + ".filteringCharset", (Callable<String>) spec::getFilteringCharset);
    }
}
