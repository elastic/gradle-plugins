package co.elatic.gradle.cig;

import org.gradle.api.Plugin;
import org.gradle.api.Project;
import org.gradle.api.tasks.TaskProvider;

public class CheckInGeneratedPlugin implements Plugin<Project> {

    @Override
    public void apply(Project target) {
        final CheckInGeneratedExtension extension = target.getExtensions()
                .create("checkInGenerated", CheckInGeneratedExtension.class);

        target.getTasks().register("generate", CopyFileMapTask.class, copy -> {
           copy.getMap().set(extension.getMap());
           copy.dependsOn(extension.getGeneratorTask());
           copy.setDescription("Regenerates the checked in generated resources");
           copy.setGroup("generate");
        });

        TaskProvider<CompareFileMapTask> verifyGenerated = target.getTasks().register("verifyGenerated", CompareFileMapTask.class, compare -> {
            compare.setGroup("generate");
            compare.setDescription("Verify that the checked in resources match what would be generated right now");
            compare.getMap().set(extension.getMap());
        });

        target.afterEvaluate( p -> {
            extension.getGeneratorTask().get().configure(generator -> {
                // Make sure we always generate after verification, otherwise there's mo point to verify
                generator.mustRunAfter(verifyGenerated);
            });
        });

    }

}
