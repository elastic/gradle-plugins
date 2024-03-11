/*
 * Copyright Elasticsearch B.V. and/or licensed to Elasticsearch B.V. under one
 * or more contributor license agreements. See the NOTICE file distributed with
 * this work for additional information regarding copyright
 * ownership. Elasticsearch B.V. licenses this file to you under
 * the Apache License, Version 2.0 (the "License"); you may
 * not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *	http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied.  See the License for the
 * specific language governing permissions and limitations
 * under the License.
 */
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