package co.elastic.gradle.dockerbase;

import org.gradle.api.provider.Provider;
import org.gradle.api.tasks.Input;

import java.io.Serializable;
import java.net.URL;

public record OsPackageRepository(String name, Provider<URL> url) implements Serializable {
    @Input
    public String getName() {
        return name;
    }

    @Input
    public Provider<URL> getUrl() {
        return url;
    }
}

