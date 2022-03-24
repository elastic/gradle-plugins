package co.elastic.gradle.cli.base;

import org.gradle.api.provider.Property;

import java.net.MalformedURLException;
import java.net.URL;

public abstract class BaseCLiExtension {

    public BaseCLiExtension() throws MalformedURLException {
        getPattern().convention("[organisation]/releases/download/[revision]/[module]-[classifier]");
        getBaseURL().convention(new URL("https://artifactory.elastic.dev/artifactory/github-release-proxy"));
    }

    public abstract Property<URL> getBaseURL();

    public abstract Property<String> getPattern();

    public abstract Property<String> getUsername();

    public abstract Property<String> getPassword();

    public abstract Property<String> getVersion();

}
