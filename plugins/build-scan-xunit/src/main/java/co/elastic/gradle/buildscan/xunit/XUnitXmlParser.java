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

import org.gradle.api.GradleException;
import org.w3c.dom.Document;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;
import org.xml.sax.SAXException;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;
import java.io.File;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

public class XUnitXmlParser {

    public static List<TestSuite> parse(File resultFile) {
        try {
            DocumentBuilderFactory dbFactory = DocumentBuilderFactory.newInstance();
            DocumentBuilder dBuilder = dbFactory.newDocumentBuilder();
            return parseTestSuites(dBuilder.parse(resultFile));
        } catch (ParserConfigurationException | SAXException | NumberFormatException e) {
            throw new GradleException("Can't parse test results at " + resultFile, e);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    public static List<TestSuite> parseTestSuites(Document doc) {
        return nodeList(doc.getElementsByTagName("testsuite"))
                .stream()
                .map(XUnitXmlParser::parseTestSuite)
                .collect(Collectors.toList());
    }

    public static TestSuite parseTestSuite(Node node) {
        return new TestSuite(
                node.getAttributes().getNamedItem("name").getTextContent(),
                Optional.ofNullable(node.getAttributes().getNamedItem("errors"))
                        .map(Node::getTextContent)
                        .map(Integer::valueOf)
                        .orElse(0),
                Optional.ofNullable(node.getAttributes().getNamedItem("failures"))
                        .map(Node::getTextContent)
                        .map(Integer::valueOf)
                        .orElse(0),
                Optional.ofNullable(node.getAttributes().getNamedItem("skipped"))
                        .map(Node::getTextContent)
                        .map(Integer::valueOf)
                        .orElse(0),
                Optional.ofNullable(node.getAttributes().getNamedItem("timestamp"))
                        .map(Node::getTextContent)
                        .map(LocalDateTime::parse)
                        .orElse(null),
                Optional.ofNullable(node.getAttributes().getNamedItem("time"))
                        .map(Node::getTextContent)
                        .map(Double::valueOf)
                        .orElse(null),
                nodeList(node.getChildNodes())
                        .stream()
                        .filter(child -> child.getNodeName().equals("testcase"))
                        .map(XUnitXmlParser::parseTestCase)
                        .collect(Collectors.toList())
        );
    }

    public static TestCase parseTestCase(Node node) {
        List<Node> childNodes = nodeList(node.getChildNodes());
        return new TestCase(
                node.getAttributes().getNamedItem("name").getTextContent(),
                node.getAttributes().getNamedItem("classname").getTextContent(),
                Optional.ofNullable(node.getAttributes().getNamedItem("time"))
                        .map(Node::getTextContent)
                        .map(Double::valueOf)
                        .orElse(null),
                childNodes.stream()
                        .filter(child -> child.getNodeName().equals("error"))
                        .findFirst()
                        .map(XUnitXmlParser::testCaseError)
                        .orElseGet(
                                () -> childNodes.stream()
                                        .filter(child -> child.getNodeName().equals("skipped"))
                                        .findFirst()
                                        .map(XUnitXmlParser::testCaseSkipped)
                                        .orElseGet(() -> childNodes.stream()
                                                .filter(child -> child.getNodeName().equals("failure"))
                                                .findFirst()
                                                .map(XUnitXmlParser::testCaseFailure)
                                                .orElseGet(() -> testCaseSuccess(childNodes))
                                        )
                        )
        );


    }

    private static TestCaseStatus testCaseError(Node errorNode) {
        return new TestCaseError(
                Optional.ofNullable(errorNode.getAttributes().getNamedItem("message"))
                        .map(Node::getTextContent)
                        .orElse(null),
                Optional.ofNullable(errorNode.getAttributes().getNamedItem("type"))
                        .map(Node::getTextContent)
                        .orElse(null),
                Optional.ofNullable(errorNode.getTextContent()).orElse(null)
        );
    }

    private static TestCaseStatus testCaseSuccess(List<Node> childNodes) {
        return new TestCaseSuccess(
                childNodes.stream()
                        .filter(child -> child.getNodeName().equals("system-out"))
                        .findFirst()
                        .map(Node::getTextContent)
                        .orElse(null),
                childNodes.stream()
                        .filter(child -> child.getNodeName().equals("system-err"))
                        .findFirst()
                        .map(Node::getTextContent)
                        .orElse(null)
        );
    }

    private static TestCaseStatus testCaseFailure(Node errorNode) {
        return new TestCaseFailure(
                Optional.ofNullable(errorNode.getAttributes().getNamedItem("message"))
                        .map(Node::getTextContent)
                        .orElse(null),
                Optional.ofNullable(errorNode.getAttributes().getNamedItem("type"))
                        .map(Node::getTextContent)
                        .orElse(null),
                Optional.ofNullable(errorNode.getTextContent())
                        .orElse(null)

        );
    }

    private static TestCaseStatus testCaseSkipped(Node errorNode) {
        return new TestCaseSkipped(
                Optional.ofNullable(errorNode.getAttributes().getNamedItem("message"))
                        .map(Node::getTextContent)
                        .orElse(null)
        );
    }

    private static List<Node> nodeList(NodeList nodeList) {
        List<Node> result = new ArrayList<>(nodeList.getLength());
        for (int i = 0; i < nodeList.getLength(); i++) {
            result.add(nodeList.item(i));
        }
        return result;
    }
}
