package co.elastic.gradle.utils;

import org.gradle.api.GradleException;
import org.gradle.api.InvalidUserDataException;
import org.gradle.api.Project;
import org.gradle.api.Task;
import org.gradle.api.internal.tasks.testing.filter.DefaultTestFilter;
import org.gradle.api.tasks.TaskProvider;
import org.gradle.api.tasks.testing.Test;

import java.io.File;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.lang.annotation.Annotation;
import java.net.URL;
import java.net.URLClassLoader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;
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

    public static List<String> testClassNames(Set<File> classesDirs) {
        try(Stream<String> s = classesDirs.stream()
                .filter(file -> {
                    final boolean exists = file.exists();
                    return exists;
                })
                .flatMap(classesDir -> {
                    Path prefix = classesDir.toPath();
                    try {
                        return Files.walk(classesDir.toPath())
                                .map(other -> {
                                    final Path relativize = prefix.relativize(other);
                                    return relativize;
                                })
                                .filter(it -> it.toString().endsWith(".class"))
                                .map(it ->
                                        it.toString().replace("/", ".")
                                                .substring(0, it.toString().lastIndexOf(".class"))
                                );
                    } catch (IOException e) {
                        throw new UncheckedIOException(e);
                    }
                })) {
            return s.collect(Collectors.toList());
        }



    }

    public static String[] classNamesByAnnotation(Set<File> classesDir, List<URL> classpath, String annotation) {
        final URLClassLoader loader = URLClassLoader.newInstance(classpath.toArray(URL[]::new));
        Class<? extends Annotation> annotationClass;
        try {
            annotationClass = loader.loadClass(annotation).asSubclass(Annotation.class);
        } catch (ClassNotFoundException e) {
            throw new GradleException("Failed to load annotation class", e);
        }

        final List<String> strings = testClassNames(classesDir);
        return strings
                .stream()
                .filter(name -> {
                    try {
                        return loader.loadClass(name).isAnnotationPresent(annotationClass);
                    } catch (ClassNotFoundException e) {
                        throw new GradleException("Failed to load test class", e);
                    }
                })
                .toArray(String[]::new);
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
