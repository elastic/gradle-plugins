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
import org.gradle.api.tasks.testing.TestOutputEvent;
import org.gradle.api.tasks.testing.TestResult;
import org.gradle.internal.id.IdGenerator;
import org.gradle.internal.id.LongIdGenerator;

import java.io.File;


public class ExternalTestExecuter implements TestExecuter<TestExecutionSpec> {

    private final Logger log = Logging.getLogger(ExternalTestExecuter.class);

    private final File fromFile;
    private final Task generatorTask;

    public ExternalTestExecuter(File fromFile, Task generatorTask) {
        this.fromFile = fromFile;
        this.generatorTask = generatorTask;
    }

    @Override
    public void execute(TestExecutionSpec testExecutionSpec, TestResultProcessor processor) {
        if (generatorTask.getDidWork() == false) {
            // The generator task was skipped, possibly because nothing changed so we don't have to re-run the tests.
            // Exception will stop current task without failing the build
            throw new StopExecutionException();
        }
        if (fromFile.exists() == false) {
            throw new GradleException("Can't find input file " + fromFile);
        }

        IdGenerator<?> idGenerator = new LongIdGenerator();

        XUnitResult result = XUnitResult.parse(fromFile);

        DefaultTestSuiteDescriptor testSuiteDescriptor = new DefaultTestSuiteDescriptor(idGenerator.generateId(), "ImportedXUnitTests");
        processor.started(testSuiteDescriptor, new TestStartEvent(System.currentTimeMillis()));
        for (XUnitTestClass testClass : result.getTestClasses()) {
            DefaultTestClassDescriptor classDescriptor = new DefaultTestClassDescriptor(idGenerator.generateId(), testClass.getName());
            processor.started(classDescriptor, new TestStartEvent(System.currentTimeMillis(), testSuiteDescriptor.getId()));
            boolean isClassSuccess = true;
            for (XUnitTestMethod testMethod : testClass.getTestMethods()) {
                DefaultTestMethodDescriptor methodDescriptor = new DefaultTestMethodDescriptor(
                        idGenerator.generateId(),
                        testClass.getName(),
                        // Add the ski reason to the method name, as build scans only show output on failure
                        testMethod.isSkipped() ? testMethod.getName() + " (" + testMethod.getStdout() + ")" : testMethod.getName()
                );
                processor.started(methodDescriptor, new TestStartEvent(System.currentTimeMillis(), classDescriptor.getId()));
                processor.output(
                        methodDescriptor.getId(),
                        new DefaultTestOutputEvent(TestOutputEvent.Destination.StdOut, testMethod.getStdout())
                );
                processor.output(
                        methodDescriptor.getId(),
                        new DefaultTestOutputEvent(TestOutputEvent.Destination.StdErr, testMethod.getStdErr())
                );
                if (testMethod.isSuccessful()) {
                    processor.completed(
                            methodDescriptor.getId(),
                            new TestCompleteEvent(
                                    System.currentTimeMillis(),
                                    testMethod.isSkipped() ? TestResult.ResultType.SKIPPED :
                                            TestResult.ResultType.SUCCESS
                            )
                    );
                } else {
                    processor.failure(
                            methodDescriptor.getId(),
                            new ExternalTestFailureException("Test being imported failed, output follows")
                    );
                }
            }
            processor.completed(classDescriptor.getId(), new TestCompleteEvent(System.currentTimeMillis()));
        }
        processor.completed(testSuiteDescriptor.getId(), new TestCompleteEvent(System.currentTimeMillis()));
    }

    @Override
    public void stopNow() {
    }

}
