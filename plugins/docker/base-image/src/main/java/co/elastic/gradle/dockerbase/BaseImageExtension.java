package co.elastic.gradle.dockerbase;

import co.elastic.gradle.dockerbase.lockfile.Lockfile;
import co.elastic.gradle.utils.Architecture;
import co.elastic.gradle.utils.RegularFileUtils;
import co.elastic.gradle.utils.docker.ContainerImageProviderTask;
import co.elastic.gradle.utils.docker.instruction.*;
import kotlin.Pair;
import org.gradle.api.Action;
import org.gradle.api.Project;
import org.gradle.api.file.CopySpec;
import org.gradle.api.file.ProjectLayout;
import org.gradle.api.file.RegularFileProperty;
import org.gradle.api.plugins.ExtensionAware;
import org.gradle.api.provider.*;
import org.gradle.api.tasks.TaskProvider;
import org.jetbrains.annotations.NotNull;

import javax.inject.Inject;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.net.MalformedURLException;
import java.net.URL;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Set;

public abstract class BaseImageExtension implements ExtensionAware {

    private final List<ContainerImageBuildInstruction> instructions = new ArrayList<>();

    public BaseImageExtension() {
        getLockFileLocation().convention(
                getProjectLayout().getProjectDirectory()
                        .file("docker-base-image.lock")
        );

        getMirrorRepositories().convention(
                getDefaultRepos()
        );

        getPlatforms().convention(
                Set.copyOf(Arrays.stream(Architecture.values()).toList())
        );

        getDockerEphemeralMount().convention("/mnt/ephemeral");

        getMaxOutputSizeMB().convention(-1L);

        getDockerTagPrefix().convention("gradle-docker-plugin");

        getDockerTagLocalPrefix().convention("local/gradle-docker-plugin");
    }

    public abstract Property<OSDistribution> getOSDistribution();

    public abstract Property<String> getDockerEphemeralMount();

    public abstract RegularFileProperty getLockFileLocation();

    public Provider<Lockfile> getLockFile() {
        return getProviderFactory().provider(() -> {
            try {
                return Lockfile.parse(Files.newBufferedReader(RegularFileUtils.toPath(getLockFileLocation())));
            } catch (IOException e) {
                throw new UncheckedIOException("Could not read lockfile", e);
            }
        });
    }

    public abstract SetProperty<Architecture> getPlatforms();

    public abstract Property<Long> getMaxOutputSizeMB();

    public abstract Property<URL> getMirrorBaseURL();

    public abstract Property<String> getDockerTagPrefix();

    public abstract Property<String> getDockerTagLocalPrefix();

    public abstract ListProperty<OsPackageRepository> getMirrorRepositories();

    @Inject
    protected abstract ProviderFactory getProviderFactory();

    @Inject
    protected abstract ProjectLayout getProjectLayout();

    @NotNull
    private Provider<List<OsPackageRepository>> getDefaultRepos() {
        return getOSDistribution().map(osDistribution -> {
                    switch (osDistribution) {
                        case CENTOS:
                            return List.of(
                                    repo("os", "cloud-rpm/$releasever/os/$basearch"),
                                    repo("updates", "cloud-rpm/$releasever/updates/$basearch"),
                                    repo("extras", "cloud-rpm/$releasever/extras/$basearch"),
                                    repo("centosplus", "cloud-rpm/$releasever/centosplus/$basearch"),
                                    repo("infra", "cloud-rpm/$releasever/infra/$basearch/infra-common")
                            );
                        case UBUNTU:
                            return List.of(
                                    repo("main", "cloud-deb $releasever main restricted universe multiverse"),
                                    repo("updates", "cloud-deb $releasever-updates main restricted universe multiverse"),
                                    repo("security", "cloud-deb $releasever-security main restricted universe multiverse"),
                                    repo("backports", "cloud-deb $releasever-backports main restricted universe multiverse")
                            );
                        case DEBIAN:
                            return List.of(
                                    repo("main", "cloud-debian $releasever main"),
                                    repo("updates", "cloud-debian $releasever-updates main"),
                                    repo("security", "cloud-debian $releasever/updates main")
                            );
                        default:
                            throw new IllegalStateException("Can't configure default repositories " +
                                                            "for " + osDistribution + ". This is a bug."
                            );
                    }
                }
        );
    }

    //----------------\\
    //  DSL Methods   \\
    //----------------\\

    public void artifactoryRepo(String name, String remotePath) {
        getMirrorRepositories().add(repo(name, remotePath));
    }

    private OsPackageRepository repo(String name, String remotePath) {
        return new OsPackageRepository(name, getMirrorBaseURL().map(baseUrl -> {
            try {
                if (baseUrl.toString().endsWith("/")) {
                    return new URL(baseUrl + remotePath);
                } else {
                    return new URL(baseUrl + "/" + remotePath);
                }
            } catch (MalformedURLException e) {
                throw new UncheckedIOException(e);
            }
        }));
    }

    @SuppressWarnings("unused")
    public String getDockerEphemeral() {
        return getDockerEphemeralMount().get();
    }

    public Architecture getArchitecture() {
        return Architecture.current();
    }

    @SuppressWarnings("unused")
    public void fromUbuntu(String image, String version) {
        getOSDistribution().set(OSDistribution.UBUNTU);
        from(image, version);
    }

    @SuppressWarnings("unused")
    public void fromDebian(String image, String version) {
        getOSDistribution().set(OSDistribution.DEBIAN);
        from(image, version);
    }

    @SuppressWarnings("unused")
    public void fromCentos(String image, String version) {
        getOSDistribution().set(OSDistribution.CENTOS);
        from(image, version);
    }

    private void from(String image, String version) {
        // The sha comes from the lockfile
        instructions.add(new From(image, version, null));
    }

    public void from(Project otherProject) {
        final TaskProvider<ContainerImageProviderTask> localImport = otherProject.getTasks()
                .named(DockerBaseImageBuildPlugin.LOCAL_IMPORT_TASK_NAME, ContainerImageProviderTask.class);
        final TaskProvider<DockerBaseImageBuildTask> build = otherProject.getTasks()
                .named(DockerBaseImageBuildPlugin.BUILD_TASK_NAME, DockerBaseImageBuildTask.class);
        instructions.add(new FromLocalImageBuild(
                otherProject.getPath(),
                localImport.flatMap(ContainerImageProviderTask::getTag),
                localImport.flatMap(ContainerImageProviderTask::getImageId)
        ));
        getOSDistribution().set(build.flatMap(DockerBaseImageBuildTask::getOSDistribution));
    }

    public void run(List<String> commands) {
        instructions.add(new Run(commands));
    }

    @SuppressWarnings("unused")
    public void run(String... commands) {
        run(Arrays.asList(commands));
    }

    @SuppressWarnings("unused")
    public void createUser(String username, Integer userId, String group, Integer groupId) {
        instructions.add(new CreateUser(username, group, userId, groupId));
    }

    @SuppressWarnings("unused")
    public void setUser(String username) {
        instructions.add(new SetUser(username));
    }

    @SuppressWarnings("unused")
    public void env(Pair<String, String> value) {
        instructions.add(new Env(value.component1(), value.component2()));
    }

    @SuppressWarnings("unused")
    public void install(String... packages) {
        instructions.add(new Install(Arrays.asList(packages)));
    }

    @SuppressWarnings("unused")
    public void healthcheck(String cmd) {
        healthcheck(cmd, null, null, null, null);
    }

    @SuppressWarnings("unused")
    public void healthcheck(String cmd, String interval, String timeout, String startPeriod, Integer retries) {
        instructions.add(new HealthCheck(cmd, interval, timeout, startPeriod, retries));
    }

    public void copySpec(String owner, Action<CopySpec> copySpecAction) {
        final String layerName = "layer" + instructions.size();
        instructions.add(new Copy(
                copySpecAction,
                layerName,
                owner
        ));
    }

    @SuppressWarnings("unused")
    public void copySpec(Action<CopySpec> copySpec) {
        copySpec(null, copySpec);
    }

    public List<ContainerImageBuildInstruction> getInstructions() {
        return List.copyOf(instructions);
    }

    public void useDefaultRepos() {
        getMirrorRepositories().addAll(getDefaultRepos());
    }
}