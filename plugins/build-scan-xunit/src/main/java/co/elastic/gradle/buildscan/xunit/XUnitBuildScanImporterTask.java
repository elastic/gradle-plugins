package co.elastic.gradle.buildscan.xunit;

import org.gradle.api.file.FileCollection;
import org.gradle.api.internal.tasks.testing.TestExecuter;
import org.gradle.api.internal.tasks.testing.TestExecutionSpec;
import org.gradle.api.provider.ListProperty;
import org.gradle.api.provider.ProviderFactory;
import org.gradle.api.tasks.InputFiles;
import org.gradle.api.tasks.SkipWhenEmpty;
import org.gradle.api.tasks.testing.AbstractTestTask;
import org.gradle.internal.logging.progress.ProgressLoggerFactory;

import javax.inject.Inject;
import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.util.HashSet;
import java.util.List;


public abstract class XUnitBuildScanImporterTask extends AbstractTestTask {

    private final ProgressLoggerFactory getProgressLoggerFactory;

    @InputFiles
    @SkipWhenEmpty
    public abstract ListProperty<File> getFrom();

    @Inject
    public XUnitBuildScanImporterTask(ProgressLoggerFactory getProgressLoggerFactory) throws IOException {
        super();
        this.getProgressLoggerFactory = getProgressLoggerFactory;
        File binaryResultsDir = new File(
                getProject().getBuildDir(),
                "externalTestImport"
        );
        Files.createDirectories(
        binaryResultsDir.toPath()
        );
        getBinaryResultsDirectory().set(binaryResultsDir);
        getReports().getJunitXml().getRequired().set(false);
        getReports().getHtml().getRequired().set(false);
        setIgnoreFailures(true);
    }

    public void from(File file) {
        getFrom().set(List.of(file));
    }

    public void from(FileCollection file) {
        getFrom().set(getProviderFactory().provider(
                () -> file.getFiles()
        ));
    }

    @Inject
    protected abstract ProviderFactory getProviderFactory();


    @Override
    protected ProgressLoggerFactory getProgressLoggerFactory() {
        return getProgressLoggerFactory;
    }

    @Override
    protected TestExecuter<? extends TestExecutionSpec> createTestExecuter() {
        return new ExternalTestExecuter(
                new HashSet<>(getFrom().get())
        );
    }

    @Override
    protected TestExecutionSpec createTestExecutionSpec() {
        return new TestExecutionSpec() {
            @Override
            public int hashCode() {
                return super.hashCode();
            }
        };
    }

}
