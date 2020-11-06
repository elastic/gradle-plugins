/*
 *
 *  * ELASTICSEARCH CONFIDENTIAL
 *  * __________________
 *  *
 *  *  Copyright Elasticsearch B.V. All rights reserved.
 *  *
 *  * NOTICE:  All information contained herein is, and remains
 *  * the property of Elasticsearch B.V. and its suppliers, if any.
 *  * The intellectual and technical concepts contained herein
 *  * are proprietary to Elasticsearch B.V. and its suppliers and
 *  * may be covered by U.S. and Foreign Patents, patents in
 *  * process, and are protected by trade secret or copyright
 *  * law.  Dissemination of this information or reproduction of
 *  * this material is strictly forbidden unless prior written
 *  * permission is obtained from Elasticsearch B.V.
 *
 */

package co.elastic.cloud.gradle.docker;

import java.io.Serializable;
import java.util.*;
import java.util.stream.Collectors;
import java.util.stream.Stream;

public class Package implements Serializable {
    private final String name;
    private final String version;
    private final String licence;
    private final String source;

    public Package(String name, String version, String license, String source) {
        this.name = name;
        this.version = version;
        this.licence = license;
        this.source = source;
    }


    public String getName() {
        return name;
    }

    public String getVersion() {
        return version;
    }

    public String getLicence() {
        return licence;
    }

    public String getSource() {
        return source;
    }

    @Override
    public String toString() {
        return "Package{" +
                "name='" + name + '\'' +
                ", version='" + version + '\'' +
                ", licence='" + licence + '\'' +
                ", source='" + source + '\'' +
                '}';
    }

    public enum PackageInstaller {
        YUM("yum"), APT("apt-get"), APK("apk");

        private final String command;

        PackageInstaller(String command) {
            this.command = command;
        }

        public String getCommand() {
            return command;
        }
    }

    public static class PackageDsl {
        private final Map<PackageInstaller, List<Package>> packages = new LinkedHashMap<>();

        public void add(PackageInstaller installer, String name, String version, String licence, String source) {
            packages.putIfAbsent(installer, new ArrayList<>());
            packages.computeIfPresent(installer, (_installer, packages) -> Stream.concat(packages.stream(), Stream.of(new Package(name, version, licence, source))).collect(Collectors.toList()));
        }

        public void yum(String name, String version, String licence, String source) {
            add(PackageInstaller.YUM, name, version, licence, source);
        }

        public void apt(String name, String version, String licence, String source) {
            add(PackageInstaller.APT, name, version, licence, source);
        }

        public void apk(String name, String version, String licence, String source) {
            add(PackageInstaller.APK, name, version, licence, source);
        }

        public Map<PackageInstaller, List<Package>> getPackages() {
            return packages;
        }
    }
}