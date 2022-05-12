package co.elastic.gradle.vault;

import org.gradle.api.Plugin;
import org.gradle.api.Project;

import java.io.File;

public class VaultPlugin implements Plugin<Project> {
    @Override
    public void apply(Project target) {
        VaultSettingsPlugin.configureExtensions(target, new File(target.getRootDir(), ".gradle/secrets"));
    }
}
