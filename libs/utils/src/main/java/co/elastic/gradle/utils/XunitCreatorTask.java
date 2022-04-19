package co.elastic.gradle.utils;

import org.gradle.api.Task;
import org.gradle.api.provider.Provider;

import java.io.File;
import java.util.Collection;

public interface XunitCreatorTask extends Task {

    Provider<Collection<File>> getXunitFiles();

}
