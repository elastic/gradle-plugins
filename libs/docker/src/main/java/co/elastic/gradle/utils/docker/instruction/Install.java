package co.elastic.gradle.utils.docker.instruction;

import org.gradle.api.tasks.Input;
import org.gradle.api.tasks.Internal;
import org.gradle.api.tasks.Nested;

import java.io.Serializable;
import java.text.MessageFormat;
import java.util.List;

public class Install implements ContainerImageBuildInstruction {
    private final PackageInstaller installer;
    private final List<Package> packages;
    private final List<Repository> repositories;

    public Install(PackageInstaller installer, List<Repository> repositories, List<Package> packages) {
        this.installer = installer;
        this.repositories = repositories;
        this.packages = packages;
    }

    @Input
    public PackageInstaller getInstaller() {
        return installer;
    }

    @Nested
    public List<Repository> getRepositories() {
        return repositories;
    }

    @Input
    public List<Package> getPackages() {
        return packages;
    }

    public static final class Package implements Serializable {
        private final String name;
        private final String version;

        public Package(String name) {
            this(name, null);
        }

        public Package(String name, String version) {
            this.name = name;
            this.version = version;
        }

        @Input
        public String getName() {
            return name;
        }

        @Input
        public String getVersion() {
            return version;
        }
    }

    public static final class Repository implements Serializable {
        private final PackageInstaller installer;
        private final String name;
        private final String url;
        private final Object[] secrets;

        public Repository(PackageInstaller installer, String name, String url, Object... secrets) {
            this.installer = installer;
            this.name = name;
            this.url = url;
            this.secrets = secrets;
        }

        @Input
        public String getName() {
            return name;
        }

        @Input
        public String getUrl() {
            return url;
        }

        @Internal
        public String getFileName() {
            if (installer == PackageInstaller.APT) {
                return name + ".list";
            }
            return name + ".repo";
        }

        @Internal
        public String getSecretUrl() {
            return MessageFormat.format(url, secrets);
        }
    }

    public enum PackageInstaller implements Serializable {
        YUM("yum"), APT("apt-get"), APK("apk");

        private final String command;

        PackageInstaller(String command) {
            this.command = command;
        }

        public String getCommand() {
            return command;
        }
    }
}

