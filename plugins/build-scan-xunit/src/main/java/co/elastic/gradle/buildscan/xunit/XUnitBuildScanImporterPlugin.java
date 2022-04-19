package co.elastic.gradle.buildscan.xunit;

import co.elastic.gradle.utils.XunitCreatorTask;
import org.gradle.api.Plugin;
import org.gradle.api.Project;
import org.gradle.api.tasks.TaskProvider;

public class XUnitBuildScanImporterPlugin implements Plugin<Project> {
    @Override
    public void apply(Project target) {


        target.getTasks()
                .withType(XunitCreatorTask.class)
                // This will cause tasks to be created eagerly, because registering tasks is not allowed in .configureEach
                .all(
                        generatorTask -> {
                            final TaskProvider<XUnitBuildScanImporterTask> xunitImport = target.getTasks().register(
                                    generatorTask.getName() + "xunitImport",
                                    XUnitBuildScanImporterTask.class,
                                    task -> {
                                        task.getFrom().set(
                                                generatorTask.getXunitFiles()
                                        );
                                        task.onlyIf(spec -> generatorTask.getDidWork());
                                    }
                            );
                            generatorTask.finalizedBy(xunitImport);
                        }
                );
    }
}
