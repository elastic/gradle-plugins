package co.elastic.gradle.license_headers;

import org.gradle.api.file.FileTreeElement;
import org.gradle.api.file.ProjectLayout;
import org.gradle.api.file.RegularFileProperty;
import org.gradle.api.provider.ListProperty;
import org.gradle.api.specs.Spec;
import org.gradle.api.tasks.*;
import org.gradle.api.tasks.util.PatternFilterable;
import org.gradle.api.tasks.util.PatternSet;

import javax.inject.Inject;
import java.io.File;

public abstract class LicenseHeaderConfig {

    private final PatternSet patternSet = new PatternSet();

    public LicenseHeaderConfig() {
        getHeaderFile().convention(getProjectLayout().getProjectDirectory().file("src/header.txt"));
    }

    @InputFile
    @PathSensitive(PathSensitivity.NONE)
    public abstract RegularFileProperty getHeaderFile();

    @InputFiles
    @PathSensitive(PathSensitivity.RELATIVE)
    @SkipWhenEmpty
    public abstract ListProperty<File> getFiles();

    @Inject
    protected abstract ProjectLayout getProjectLayout();

    @Internal
    protected PatternFilterable getFilter() {
        return patternSet;
    }

    public PatternSet include(String... includes) {
        return patternSet.include(includes);
    }

    public PatternSet include(Iterable includes) {
        return patternSet.include(includes);
    }

    public PatternSet include(Spec<FileTreeElement> spec) {
        return patternSet.include(spec);
    }

    public PatternSet exclude(String... excludes) {
        return patternSet.exclude(excludes);
    }

    public PatternSet exclude(Iterable excludes) {
        return patternSet.exclude(excludes);
    }

    public PatternSet exclude(Spec<FileTreeElement> spec) {
        return patternSet.exclude(spec);
    }
}
