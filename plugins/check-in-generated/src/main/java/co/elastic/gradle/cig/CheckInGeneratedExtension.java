package co.elastic.gradle.cig;

import org.gradle.api.Task;
import org.gradle.api.plugins.ExtensionAware;
import org.gradle.api.provider.MapProperty;
import org.gradle.api.provider.Property;
import org.gradle.api.tasks.TaskProvider;

import java.io.File;


public abstract class CheckInGeneratedExtension implements ExtensionAware {

    public abstract Property<TaskProvider<Task>> getGeneratorTask();

    public abstract MapProperty<File, File> getMap();

}
