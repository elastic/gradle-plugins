package co.elastic.gradle.license_headers;

import co.elastic.gradle.lifecycle.LifecyclePlugin;
import co.elastic.gradle.lifecycle.MultiArchLifecyclePlugin;
import org.gradle.api.Plugin;
import org.gradle.api.Project;
import org.gradle.api.tasks.TaskProvider;

public class LicenseHeadersPlugin implements Plugin<Project> {

    @Override
    public void apply(Project target) {
        final LicenseHeadersExtension extension = target.getExtensions().create("licenseHeaders", LicenseHeadersExtension.class);

        final TaskProvider<CheckLicenseHeadersTask> checkLicenseHeaders = target.getTasks().register(
                "checkLicenseHeaders",
                CheckLicenseHeadersTask.class,
                task -> task.getConfigs().set(extension.getConfigs())
        );

        target.getTasks().register(
                "fixLicenseHeaders",
                FixLicenseHeadersTask.class,
                task -> task.getConfigs().set(extension.getConfigs())
        );

        LifecyclePlugin.check(target, checkLicenseHeaders);
        LifecyclePlugin.autoFix(target, checkLicenseHeaders);
        MultiArchLifecyclePlugin.checkPlatformIndependent(target, checkLicenseHeaders);
    }

}
