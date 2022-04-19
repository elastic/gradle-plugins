package co.elastic.gradle.dockerbase.lockfile;

import co.elastic.gradle.dockerbase.OSDistribution;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

public class UnchangingPackagesTest {
    @Test
    public void shouldConvertToAptPackage() {
        final UnchangingPackage pkg = new UnchangingPackage("jq", "1.5", "rel", "amd64");
        assertEquals("jq", pkg.getName());
        assertEquals("1.5", pkg.getVersion());
        assertEquals("rel", pkg.getRelease());
        assertEquals("amd64", pkg.getArchitecture());
        assertEquals("jq=1.5-rel", pkg.getPackageName(OSDistribution.DEBIAN));
    }

    @Test
    public void shouldConvertToAptPackageNoRel() {
        final UnchangingPackage pkg = new UnchangingPackage("jq", "1.5", "", "amd64");
        assertEquals("jq", pkg.getName());
        assertEquals("1.5", pkg.getVersion());
        assertEquals("", pkg.getRelease());
        assertEquals("amd64", pkg.getArchitecture());
        assertEquals("jq=1.5", pkg.getPackageName(OSDistribution.DEBIAN));
    }

    @Test
    public void shouldConvertToYumPackage() {
        assertEquals(
                "jq-1.5-12.el8.x86_64",
                new UnchangingPackage("jq", "1.5", "12.el8", "x86_64")
                        .getPackageName(OSDistribution.CENTOS)
        );
    }
}
