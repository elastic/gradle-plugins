package co.elastic.gradle.cli.base;

import org.gradle.api.GradleException;
import org.gradle.api.Plugin;
import org.gradle.api.Project;

public class DontApplyPlugin implements Plugin<Project> {

    @Override
    public void apply(Project target) {
        throw new GradleException("This plugin is really a library not ment to be applied");
    }
}
