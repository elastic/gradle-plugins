package co.elastic.gradle.vault;

import org.gradle.api.GradleException;
import org.gradle.api.Plugin;
import org.gradle.api.Project;
import org.gradle.api.initialization.Settings;
import org.gradle.api.plugins.ExtensionAware;
import org.gradle.api.plugins.PluginAware;

import javax.annotation.Nonnull;
import java.io.File;

public class VaultPlugin implements Plugin<PluginAware> {

    final static String EXTENSION_NAME = "vault";

    @Override
    public void apply(@Nonnull PluginAware target) {
        final File rootDir;
        if (target instanceof Settings settings) {
            rootDir = settings.getRootDir();
        } else if (target instanceof Project project) {
            rootDir = project.getRootDir();
        } else {
            throw new GradleException("Can't apply plugin to " + target.getClass());
        }
        final VaultExtension extension = ((ExtensionAware) target).getExtensions().create(
                EXTENSION_NAME,
                VaultExtension.class,
                new File(rootDir, ".gradle/secrets")
        );
        extension.getExtensions().create("auth", VaultAuthenticationExtension.class);
    }

}
