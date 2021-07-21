package co.elastic.cloud.gradle.util;

import org.gradle.api.InvalidUserDataException;
import org.gradle.api.Project;
import org.gradle.api.Task;
import org.gradle.api.internal.tasks.testing.filter.DefaultTestFilter;
import org.gradle.api.specs.Spec;
import org.gradle.api.tasks.TaskProvider;
import org.gradle.api.tasks.testing.Test;

import java.io.File;
import java.nio.file.Path;
import java.util.Collection;
import java.util.Collections;
import java.util.HashSet;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;

public class GradleUtils {

    public static boolean areTestsFiltered(Test task) {
        return Optional.ofNullable(task.getFilter())
                .filter(it -> it instanceof DefaultTestFilter)
                .map(it -> (DefaultTestFilter) it)
                .map(it -> {
                    Set<String> filters = new HashSet<>();
                    filters.addAll(Optional.ofNullable(it.getCommandLineIncludePatterns()).orElse(Collections.emptySet()));
                    filters.addAll(Optional.ofNullable(it.getIncludePatterns()).orElse(Collections.emptySet()));
                    return filters;
                })
                .map(filters -> !filters.isEmpty())
                .orElse(false);
    }

    public static boolean isCi() {
        return System.getenv("BUILD_URL") != null;
    }

    public static String listPathsRelativeToProject(Project project, Collection<Path> files) {
        if (files.isEmpty()) { return ""; }
        Path projectPath = project.getProjectDir().toPath();
        return "\n    " + files.stream()
                .map(path -> projectPath.relativize(path))
                .map(Path::toString)
                .collect(Collectors.joining("\n    ,"));
    }

    public static TaskProvider<Task> registerOrGet(Project project, String taskName) {
        try {
            return project.getTasks().register(taskName);
        } catch (InvalidUserDataException e) {
            return project.getTasks().named(taskName);
        }
    }
}
