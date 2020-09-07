package co.elastic.cloud.gradle.testing.buildscan;

import org.gradle.api.GradleException;
import org.gradle.api.Task;
import org.gradle.api.internal.tasks.testing.DefaultTestClassDescriptor;
import org.gradle.api.internal.tasks.testing.DefaultTestMethodDescriptor;
import org.gradle.api.internal.tasks.testing.DefaultTestOutputEvent;
import org.gradle.api.internal.tasks.testing.DefaultTestSuiteDescriptor;
import org.gradle.api.internal.tasks.testing.TestCompleteEvent;
import org.gradle.api.internal.tasks.testing.TestExecuter;
import org.gradle.api.internal.tasks.testing.TestExecutionSpec;
import org.gradle.api.internal.tasks.testing.TestResultProcessor;
import org.gradle.api.internal.tasks.testing.TestStartEvent;
import org.gradle.api.logging.Logger;
import org.gradle.api.logging.Logging;
import org.gradle.api.tasks.StopExecutionException;
import org.gradle.api.tasks.TaskProvider;
import org.gradle.api.tasks.testing.TestOutputEvent;
import org.gradle.api.tasks.testing.TestResult;
import org.gradle.internal.id.IdGenerator;
import org.gradle.internal.id.LongIdGenerator;

import java.io.File;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;


public class ExternalTestExecuter implements TestExecuter<TestExecutionSpec> {

    private final Logger log = Logging.getLogger(ExternalTestExecuter.class);

    private final Set<File> fromFiles;
    private final TaskProvider<? extends Task> generatorTask;

    public ExternalTestExecuter(Set<File> fromFile, TaskProvider<? extends Task> generatorTask) {
        this.fromFiles = fromFile;
        this.generatorTask = generatorTask;
    }

    @Override
    public void execute(TestExecutionSpec testExecutionSpec, TestResultProcessor processor) {
        if (!generatorTask.get().getDidWork()) {
            // The generator task was skipped, possibly because nothing changed so we don't have to re-run the tests.
            // Exception will stop current task without failing the build
            throw new StopExecutionException();
        }

        List<String> missingFiles = fromFiles.stream()
                .filter(file -> !file.exists())
                .map(File::getAbsolutePath)
                .collect(Collectors.toList());
        if (missingFiles.size() > 0) {
            throw new GradleException("Can't find input files " + String.join(" ", missingFiles));
        }

        IdGenerator<?> idGenerator = new LongIdGenerator();

        for (File fromFile : fromFiles) {
            DefaultTestSuiteDescriptor testSuiteDescriptor = new DefaultTestSuiteDescriptor(idGenerator.generateId(), "ImportedXUnitTests");
            processor.started(testSuiteDescriptor, new TestStartEvent(System.currentTimeMillis()));
            XUnitXmlParser.parse(fromFile).forEach(testSuite -> {
                DefaultTestClassDescriptor suiteDescriptor = new DefaultTestClassDescriptor(idGenerator.generateId(), testSuite.getName());
                processor.started(suiteDescriptor, new TestStartEvent(System.currentTimeMillis()));
                testSuite.getTests().forEach(testCase -> {
                    DefaultTestMethodDescriptor methodDescriptor = new DefaultTestMethodDescriptor(
                            idGenerator.generateId(),
                            testSuite.getName(),
                            testCase.getName());
                    processor.started(methodDescriptor, new TestStartEvent(System.currentTimeMillis(), suiteDescriptor.getId()));
                    // Cannot switch on types ...
                    if (testCase.getStatus() instanceof TestCaseSuccess) {
                        TestCaseSuccess success = (TestCaseSuccess) testCase.getStatus();
                        success.getStdout().ifPresent(stdout -> processor.output(
                                methodDescriptor.getId(),
                                new DefaultTestOutputEvent(TestOutputEvent.Destination.StdOut, stdout)
                        ));
                        success.getStderr().ifPresent(stderr -> processor.output(
                                methodDescriptor.getId(),
                                new DefaultTestOutputEvent(TestOutputEvent.Destination.StdErr, stderr)
                        ));
                        processor.completed(
                                methodDescriptor.getId(),
                                new TestCompleteEvent(System.currentTimeMillis(), TestResult.ResultType.SUCCESS)
                        );
                    } else if (testCase.getStatus() instanceof TestCaseFailure) {
                        TestCaseFailure failure = (TestCaseFailure) testCase.getStatus();
                        processor.failure(
                                methodDescriptor.getId(), new ExternalTestFailureException(
                                        "Test case being imported failed (" + failure.getType().orElse("Untyped") + "): " +
                                                failure.getMessage().orElse("") + " " +
                                                failure.getDescription().orElse(""))
                        );
                    } else if (testCase.getStatus() instanceof TestCaseError) {
                        TestCaseError error = (TestCaseError) testCase.getStatus();
                        processor.failure(
                                methodDescriptor.getId(), new ExternalTestFailureException(
                                        "Test case being imported failed (" + error.getType().orElse("Untyped") + "): " +
                                                error.getMessage().orElse("") +
                                                error.getDescription().map(desc -> "\n"+desc).orElse(""))
                        );
                    } else if (testCase.getStatus() instanceof TestCaseSkipped) {
                        TestCaseSkipped skipped = (TestCaseSkipped) testCase.getStatus();
                        skipped.getMessage().ifPresent(message -> processor.output(
                                methodDescriptor.getId(),
                                new DefaultTestOutputEvent(TestOutputEvent.Destination.StdOut, message)
                        ));
                        processor.completed(
                                methodDescriptor.getId(),
                                new TestCompleteEvent(System.currentTimeMillis(), TestResult.ResultType.SKIPPED)
                        );
                    }
                });
                // If no methods was run check that the all class don't have errors or failures
                if (testSuite.getTests().size() == 0 && (testSuite.getErrors() > 0 || testSuite.getFailures() > 0)) {
                    processor.failure(
                            suiteDescriptor.getId(),
                            new ExternalTestFailureException("TestSuite being imported has errors")
                    );
                }
                processor.completed(suiteDescriptor.getId(), new TestCompleteEvent(System.currentTimeMillis()));
            });
        }
    }

    @Override
    public void stopNow() {
    }

}
