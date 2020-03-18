package co.elastic.cloud.gradle.testing.buildscan;

import org.gradle.api.internal.tasks.testing.DefaultTestClassDescriptor;
import org.gradle.api.internal.tasks.testing.DefaultTestMethodDescriptor;
import org.gradle.api.internal.tasks.testing.DefaultTestSuiteDescriptor;
import org.gradle.api.internal.tasks.testing.TestCompleteEvent;
import org.gradle.api.internal.tasks.testing.TestExecuter;
import org.gradle.api.internal.tasks.testing.TestExecutionSpec;
import org.gradle.api.internal.tasks.testing.TestResultProcessor;
import org.gradle.api.internal.tasks.testing.TestStartEvent;
import org.gradle.api.tasks.testing.TestResult;
import org.gradle.internal.id.IdGenerator;
import org.gradle.internal.id.LongIdGenerator;

import javax.inject.Inject;

public class ExternalTestExecuter implements TestExecuter<TestExecutionSpec> {

    @Override
    public void execute(TestExecutionSpec testExecutionSpec, TestResultProcessor processor) {
        IdGenerator<?> idGenerator = new LongIdGenerator();

        DefaultTestSuiteDescriptor testSuiteDescriptor = new DefaultTestSuiteDescriptor(idGenerator.generateId(), "ImportedXUnitTests");
        DefaultTestClassDescriptor classDescriptor = new DefaultTestClassDescriptor(idGenerator.generateId(), "foo.class");
        DefaultTestMethodDescriptor methodDescriptor = new DefaultTestMethodDescriptor(idGenerator.generateId(), "foo.class", "method");

        processor.started(testSuiteDescriptor, new TestStartEvent(System.currentTimeMillis()));
        processor.started(classDescriptor, new TestStartEvent(System.currentTimeMillis(), testSuiteDescriptor.getId()));
        processor.started(methodDescriptor, new TestStartEvent(System.currentTimeMillis(), classDescriptor.getId()));

        processor.completed(methodDescriptor.getId(), new TestCompleteEvent(System.currentTimeMillis(), TestResult.ResultType.SUCCESS));
        processor.completed(classDescriptor.getId(), new TestCompleteEvent(System.currentTimeMillis(), TestResult.ResultType.SUCCESS));
        processor.completed(testSuiteDescriptor.getId(), new TestCompleteEvent(System.currentTimeMillis()));
    }

    @Override
    public void stopNow() {
    }
}
