package co.elastic.gradle.dockercomponent;

import co.elastic.gradle.dockercomponent.lockfile.ComponentLockfile;
import co.elastic.gradle.utils.RegularFileUtils;
import org.gradle.api.DefaultTask;
import org.gradle.api.file.RegularFileProperty;
import org.gradle.api.tasks.InputFiles;
import org.gradle.api.tasks.SkipWhenEmpty;
import org.gradle.api.tasks.TaskAction;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

public abstract class ComponentPullTask extends DefaultTask {

    @InputFiles
    @SkipWhenEmpty
    public abstract RegularFileProperty getLockfileLocation();

    @TaskAction
    public void pullImages() throws IOException {
        final Path lockfileLocation = RegularFileUtils.toPath(getLockfileLocation());
        final ComponentLockfile lockFile = ComponentLockfile.parse(Files.newBufferedReader(lockfileLocation));
        final JibActions actions = new JibActions();
        lockFile.images().values().forEach(ref -> {
            final String format = String.format("%s:%s@%s", ref.getRepository(), ref.getTag(), ref.getDigest());
            getLogger().lifecycle("Pulling base layers for {} into the jib cache", format);
            actions.pullImage(format);
        });
    }

}
