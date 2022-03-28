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

import co.elastic.gradle.utils.Architecture;
import co.elastic.gradle.utils.GradleUtils;
import org.gradle.api.Project;
import org.gradle.api.Task;
import org.gradle.api.file.DuplicatesStrategy;
import org.gradle.api.file.FileTree;
import org.gradle.api.internal.file.copy.CopySpecInternal;
import org.gradle.api.internal.file.copy.CopySpecResolver;
import org.gradle.api.tasks.PathSensitivity;
import org.gradle.api.tasks.TaskProvider;

import java.util.Optional;
import java.util.concurrent.Callable;

public class DockerPluginConventions {

    // See https://github.com/moby/moby/blob/master/image/spec/v1.2.md#terminology for Repository and Tag terminology
    // Tag : [Registry]/[Repository]:[Version]
    // Repository : [OrganizationPathComponent]/[NamePathComponent]

    public static String getRegistry() {
        return "docker.elastic.co";
    }

    public static String getOrganizationPathComponent(Project project) {
        if (GradleUtils.isCi()) {
            // FIXME: This needs to be configurable!!
            return "cloud-ci";
        } else {
            return Optional.ofNullable(project.findProperty("co.elastic.docker.push.organization"))
                    .map(String::valueOf)
                    .orElse("gradle");
        }
    }

    public static String componentImageTag(Project project, Architecture architecture) {
        return getRegistry() + "/" +
                getOrganizationPathComponent(project) + "/" +
                project.getName() + "-" + architecture.dockerName() +
                ":" + project.getVersion();
    }

    public static String baseImageTag(Project project, Architecture architecture) {
        return getRegistry() + "/" +
                getOrganizationPathComponent(project) + "/" +
                project.getName() + "-" + architecture.dockerName() +
                ":" + project.getVersion();
    }

    public static String manifestListTag(Project project) {
        return getRegistry() + "/" +
                getOrganizationPathComponent(project) + "/" +
                project.getName() +
                ":" + project.getVersion();
    }

    public static String imageTag(Project gradleProject) {
        return imageTag(gradleProject, null, null);
    }

    public static String imageTagWithQualifier(Project project, String qualifier) {
        return imageTag(project, qualifier, null);
    }

    public static String imageTagWithAlias(Project project, String alias) {
        return imageTag(project, null, alias);
    }

    private static String imageTag(Project project, String qualifier, String alias) {
        return getRegistry() + "/" +
                getOrganizationPathComponent(project) + "/" +
                Optional.ofNullable(alias).orElse(project.getName()) +
                Optional.ofNullable(qualifier).orElse("") + ":" +
                project.getVersion();
    }

    public static String localImportImageTag(Project project) {
        return localImportImageTag(project, null);
    }

    public static String localImportImageTag(Project project, String qualifier) {
        return getRegistry() + "/" +
                "gradle/" +
                project.getName() +
                Optional.ofNullable(qualifier).map(it -> "-" + it).orElse("") + ":" +
                "latest";
    }

    public static CopySpecInternal.CopySpecListener mapCopySpecToTaskInputs(TaskProvider<? extends Task> taskProvider) {
        return (path, spec) -> {
            doMapCopySpecToTaskInoputs(path, spec, taskProvider.get());
        };
    }

    public static CopySpecInternal.CopySpecListener mapCopySpecToTaskInputs(Task task) {
        return (path, spec) -> {
            doMapCopySpecToTaskInoputs(path, spec, task);
        };
    }

    private static void doMapCopySpecToTaskInoputs(CopySpecInternal.CopySpecAddress path, CopySpecInternal spec, Task task) {
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
