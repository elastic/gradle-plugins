package co.elastic.gradle.dockerbase.lockfile;

import com.fasterxml.jackson.annotation.JsonCreator;
import org.gradle.api.tasks.Nested;
import org.gradle.util.internal.VersionNumber;

import java.io.Serializable;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

public record Packages(
        List<UnchangingPackage> packages
) implements Serializable {

    @JsonCreator
    public Packages {
        if (packages.size() != getUniquePackagesWithMaxVersion(packages).size()) {
            throw new IllegalStateException("Multiple packages have the same name");
        }
    }

    public static List<UnchangingPackage> getUniquePackagesWithMaxVersion(List<UnchangingPackage> packages) {
        final Set<String> nameSet = packages.stream().map(UnchangingPackage::getName)
                .collect(Collectors.toSet());
        return nameSet.stream()
                .map(name -> packages.stream().filter(pkg -> pkg.name().equals(name)).toList())
                .map(list -> list.stream()
                        .max(Comparator.comparing(a -> VersionNumber.parse(a.getVersion())))
                        .orElseThrow(IllegalStateException::new))
                .toList();
    }

    @Nested
    public List<UnchangingPackage> getPackages() {
        return packages();
    }

    public Optional<UnchangingPackage> findByName(String name) {
        return getPackages()
                .stream()
                .filter(each -> each.getName().equals(name))
                .findFirst();
    }

}
