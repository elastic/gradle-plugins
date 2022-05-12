package co.elastic.gradle.wrapper;

import org.gradle.api.Plugin;
import org.gradle.api.Project;
import org.gradle.api.tasks.TaskProvider;
import org.gradle.api.tasks.wrapper.Wrapper;

public class WrapperPlugin implements Plugin<Project> {
    @Override
    public void apply(Project target) {
        target.getPluginManager().apply(org.gradle.buildinit.plugins.WrapperPlugin.class);
        if (target.getParent() == null) {
            final TaskProvider<ProvisionJDKInWrapperTask> provisionJdk = target.getTasks()
                    .register("wrapperProvisionJdk", ProvisionJDKInWrapperTask.class, task -> {
                        task.setGroup("Build Setup");
                        task.setDescription("Generates Gradle wrapper files.");
                        task.dependsOn("wrapper");
                    });
            target.getTasks().withType(Wrapper.class, wrapper -> {
                wrapper.finalizedBy(provisionJdk);
            });
        }
    }
}
