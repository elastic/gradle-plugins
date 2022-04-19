package co.elastic.gradle.buildscan.xunit;

import org.gradle.api.GradleException;
import org.gradle.api.internal.tasks.testing.*;
import org.gradle.api.logging.Logger;
import org.gradle.api.logging.Logging;
import org.gradle.api.tasks.testing.TestOutputEvent;
import org.gradle.api.tasks.testing.TestResult;
import org.gradle.internal.id.IdGenerator;
import org.gradle.internal.id.LongIdGenerator;

import java.io.File;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;


public class ExternalTestExecuter implements TestExecuter<TestExecutionSpec> {

    private final Set<File> fromFiles;
    private static final Logger logger = Logging.getLogger(ExternalTestExecuter.class);

    public ExternalTestExecuter(Set<File> fromFile) {
        this.fromFiles = fromFile;

    }

    @Override
    public void execute(TestExecutionSpec testExecutionSpec, TestResultProcessor processor) {
        List<String> missingFiles = fromFiles.stream()
                .filter(file -> !file.exists())
                .map(File::getAbsolutePath)
                .collect(Collectors.toList());
        if (missingFiles.size() > 0) {
            throw new GradleException("Can't find input files " + String.join(" ", missingFiles));
        }

        IdGenerator<?> idGenerator = new LongIdGenerator();

        fromFiles.stream()
                .peek(file -> logger.lifecycle("Loading results from {}", file))
                .flatMap(resultFile -> XUnitXmlParser.parse(resultFile).stream())
                .forEach(testSuite -> {
                    DefaultTestClassDescriptor suiteDescriptor = new DefaultTestClassDescriptor(idGenerator.generateId(), testSuite.name());
                    LocalDateTime suiteStartTime = testSuite.startedTime(LocalDateTime::now);
                    processor.started(suiteDescriptor, new TestStartEvent(toEpochMilli(suiteStartTime)));

                    testSuite.tests().forEach(testCase -> {

                        DefaultTestMethodDescriptor methodDescriptor = new DefaultTestMethodDescriptor(
                                idGenerator.generateId(),
                                testSuite.name(),
                                testCase.name());

                        processor.started(methodDescriptor, new TestStartEvent(toEpochMilli(suiteStartTime), suiteDescriptor.getId()));

                        // Cannot switch on types ...
                        if (testCase.status() instanceof TestCaseSuccess success) {
                            Optional.of(success.stdout()).ifPresent(stdout -> processor.output(
                                    methodDescriptor.getId(),
                                    new DefaultTestOutputEvent(TestOutputEvent.Destination.StdOut, stdout)
                            ));
                            Optional.of(success.stderr()).ifPresent(stderr -> processor.output(
                                    methodDescriptor.getId(),
                                    new DefaultTestOutputEvent(TestOutputEvent.Destination.StdErr, stderr)
                            ));
                            processor.completed(
                                    methodDescriptor.getId(),
                                    new TestCompleteEvent(
                                            toEpochMilli(testCase.endTime(suiteStartTime)),
                                            TestResult.ResultType.SUCCESS
                                    )
                            );
                        } else if (testCase.status() instanceof TestCaseFailure failure) {
                            processor.failure(
                                    methodDescriptor.getId(),
                                    new ExternalTestFailureException(
                                            "Test case being imported failed (" + Optional.ofNullable(failure.type()).orElse("Untyped") + "): " +
                                            Optional.ofNullable(failure.message()).orElse("") + " " +
                                            Optional.ofNullable(failure.description()).orElse("")
                                    )
                            );
                        } else if (testCase.status() instanceof TestCaseError error) {
                            processor.failure(
                                    methodDescriptor.getId(),
                                    new ExternalTestFailureException(
                                            "Test case being imported failed (" + Optional.ofNullable(error.type()).orElse("Untyped") + "): " +
                                            Optional.ofNullable(error.message()).orElse("") +
                                            Optional.ofNullable(error.description()).map(desc -> "\n" + desc).orElse("")
                                    )
                            );
                        } else if (testCase.status() instanceof TestCaseSkipped skipped) {
                            Optional.ofNullable(skipped.message()).ifPresent(message -> processor.output(
                                    methodDescriptor.getId(),
                                    new DefaultTestOutputEvent(TestOutputEvent.Destination.StdOut, message)
                            ));
                            processor.completed(
                                    methodDescriptor.getId(),
                                    new TestCompleteEvent(toEpochMilli(testCase.endTime(suiteStartTime)), TestResult.ResultType.SKIPPED)
                            );
                        }
                    });
                    processor.completed(suiteDescriptor.getId(), new TestCompleteEvent(toEpochMilli(testSuite.endTime(() -> suiteStartTime))));
                });
    }


    private long toEpochMilli(LocalDateTime time) {
        return time.atZone(ZoneId.systemDefault()).toEpochSecond();
    }

    @Override
    public void stopNow() {
    }

}
