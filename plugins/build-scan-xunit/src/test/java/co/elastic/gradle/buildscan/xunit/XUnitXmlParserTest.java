package co.elastic.gradle.buildscan.xunit;

import org.junit.jupiter.api.Test;

import java.io.File;
import java.net.URISyntaxException;
import java.time.LocalDateTime;
import java.time.Month;
import java.util.List;
import java.util.Objects;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertIterableEquals;

class XUnitXmlParserTest {

    @Test
    public void testParseSampleJUnitXUnitXml() throws URISyntaxException {
        List<TestSuite> testSuites = XUnitXmlParser.parse(
                new File(Objects.requireNonNull(getClass().getResource("/sample-junit-testsuite.xml")).toURI())
        );

        assertEquals(1, testSuites.size());
        TestSuite testSuite = testSuites.get(0);

        assertEquals("BasicDeploymentAdminUserSpec", testSuite.name());
        assertEquals(1, testSuite.errors());
        assertEquals(1, testSuite.failures());
        assertEquals(1, testSuite.skipped());
        assertEquals(285.673, testSuite.time());
        assertEquals(
                LocalDateTime.of(2020, Month.AUGUST, 26, 18, 19, 44),
                testSuite.timestamp()
        );

        assertIterableEquals(
                List.of(
                        new TestCase(
                                "testMethod1",
                                "co.elastic.cloud.functional.deployments.elasticsearch.BasicDeploymentAdminUserSpec",
                                0.006,
                                new TestCaseSuccess("STDOUT text", "STDERR text")
                        ),
                        new TestCase(
                                "testMethod2",
                                "co.elastic.cloud.functional.deployments.elasticsearch.BasicDeploymentAdminUserSpec",
                                0.001,
                                new TestCaseError(
                                        "error message",
                                        "error message type",
                                        "error description"
                                )
                        ),
                        new TestCase(
                                "testMethod3",
                                "co.elastic.cloud.functional.deployments.elasticsearch.BasicDeploymentAdminUserSpec",
                                0.001,
                                new TestCaseSkipped("skipped message")
                        ),
                        new TestCase(
                                "testMethod4",
                                "co.elastic.cloud.functional.deployments.elasticsearch.BasicDeploymentAdminUserSpec",
                                0.0,
                                new TestCaseFailure(
                                        "failure message",
                                        "some failure type",
                                        "failure description"
                                )
                        )
                ),
                testSuite.tests()
        );
    }

    @Test
    public void testParseSampleJestXUnitXml() throws URISyntaxException {
        List<TestSuite> testSuites = XUnitXmlParser.parse(
                new File(Objects.requireNonNull(getClass().getResource("/sample-jest-result.xml")).toURI())
        );

        assertEquals(2, testSuites.size());
        TestSuite testSuite = testSuites.get(0);

        assertEquals("FileName.test.js", testSuite.name());
        assertEquals(1, testSuite.errors());
        assertEquals(1, testSuite.failures());
        assertEquals(1, testSuite.skipped());
        assertEquals(6.568, testSuite.time());
        assertEquals(LocalDateTime.of(2020, Month.AUGUST, 26, 16, 18, 47), testSuite.timestamp());

        assertIterableEquals(List.of(
                        new TestCase(
                                " success test name",
                                " success test name",
                                0.006,
                                new TestCaseSuccess("STDOUT text", "STDERR text")
                        ),
                        new TestCase(
                                " error test name",
                                " error test name",
                                0.061,
                                new TestCaseError(
                                        "error message",
                                        "error message type",
                                        "error description"
                                )
                        ),
                        new TestCase(
                                " skipped test name",
                                " skipped test name",
                                0.001,
                                new TestCaseSkipped("skipped message")
                        ),
                        new TestCase(
                                " failure test name",
                                " failure test name",
                                0.0,
                                new TestCaseFailure(
                                        "failure message",
                                        "some failure type",
                                        "failure description"

                                )
                        )
                ),
                testSuite.tests()
        );

        testSuite = testSuites.get(1);

        assertEquals("FileName.test.tsx", testSuite.name());
        assertEquals(1, testSuite.errors());
        assertEquals(1, testSuite.failures());
        assertEquals(1, testSuite.skipped());
        assertEquals(6.568, testSuite.time());
        assertEquals(
                LocalDateTime.of(2020, Month.AUGUST, 26, 16, 18, 47),
                testSuite.timestamp()
        );

        assertIterableEquals(List.of(
                        new TestCase(
                                " success test name",
                                " success test name",
                                0.006,
                                new TestCaseSuccess(
                                        "STDOUT text",
                                        "STDERR text"
                                )

                        ),
                        new TestCase(
                                " error test name",
                                " error test name",
                                0.061,
                                new TestCaseError(
                                        "error message",
                                        "error message type",
                                        "error description"
                                )
                        ),
                        new TestCase(
                                " skipped test name",
                                " skipped test name",
                                0.001,
                                new TestCaseSkipped("skipped message")

                        ),
                        new TestCase(
                                " failure test name",
                                " failure test name",
                                0.0,
                                new TestCaseFailure(
                                        "failure message",
                                        "some failure type",
                                        "failure description"
                                )
                        )
                ),
                testSuite.tests()
        );
    }

}