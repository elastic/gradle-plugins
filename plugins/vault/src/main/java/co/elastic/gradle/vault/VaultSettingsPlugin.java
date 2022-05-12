package co.elastic.gradle.vault;

import org.gradle.api.Plugin;
import org.gradle.api.initialization.Settings;
import org.gradle.api.plugins.ExtensionAware;

import java.io.File;


abstract public class VaultSettingsPlugin implements Plugin<Settings>{

    final static String EXTENSION_NAME = "vault";

    @SuppressWarnings("unchecked")
    @Override
    public void apply(Settings target) {
        VaultSettingsPlugin.configureExtensions(target, new File(target.getRootDir(), ".gradle/secrets"));
    }

    static void configureExtensions(ExtensionAware target, File cacheDir) {
        target.getExtensions().create(
                EXTENSION_NAME,
                VaultExtension.class,
                cacheDir
        );

        ((ExtensionAware) target.getExtensions().getByName(EXTENSION_NAME))
                .getExtensions().create("auth", VaultAuthenticationExtension.class);
    }

}
