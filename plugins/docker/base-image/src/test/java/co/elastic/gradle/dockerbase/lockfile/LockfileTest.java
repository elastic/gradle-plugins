package co.elastic.gradle.dockerbase.lockfile;

import co.elastic.gradle.utils.Architecture;
import org.jetbrains.annotations.NotNull;
import org.junit.jupiter.api.Test;

import java.io.*;
import java.util.List;
import java.util.Map;
import java.util.Objects;

import static org.junit.jupiter.api.Assertions.assertEquals;

public class LockfileTest {
    @Test
    public void shouldSerdeYaml() throws IOException {
        Lockfile lockfile = getSampleLockfile();
        assertSampleLockfile(lockfile);
        try (ByteArrayOutputStream outStream = new ByteArrayOutputStream()) {
            Lockfile.write(lockfile, new OutputStreamWriter(outStream));
            try (ByteArrayInputStream inStream = new ByteArrayInputStream(outStream.toByteArray())) {
                assertSampleLockfile(Lockfile.parse(new InputStreamReader(inStream)));
            }
        }
    }

    @Test
    public void keepFileFormatCompatability() throws IOException {
        final Lockfile lockfile = Lockfile.parse(new InputStreamReader(
                Objects.requireNonNull(getClass().getResourceAsStream("/lockfile.yaml"))
        ));
        assertSampleLockfile(lockfile);
        assertEquals(getSampleLockfile(), lockfile);
    }

    @NotNull
    private Lockfile getSampleLockfile() {
        return new Lockfile(
                Map.of(
                        Architecture.X86_64,
                        new Packages(
                                List.of(
                                        new UnchangingPackage(
                                                "jq", "1.5", "12.el8", "x86_64"
                                        )
                                )
                        ),

                        Architecture.AARCH64,
                        new Packages(
                                List.of(
                                        new UnchangingPackage(
                                                "jq", "1.5", "12.el8", "aarch64"
                                        )
                                )
                        )
                ),
                Map.of(
                        Architecture.X86_64,
                        new UnchangingContainerReference(
                                "repo_x86", "tag_x86", "digest_x86"
                        ),

                        Architecture.AARCH64,
                        new UnchangingContainerReference("repo_arm", "tag_arm", "digest_arm")

                )
        );
    }

    private void assertSampleLockfile(Lockfile lockfile) {
        Packages x86 = lockfile.getPackages().get(Architecture.X86_64);
        Packages arm = lockfile.getPackages().get(Architecture.AARCH64);

        final UnchangingContainerReference armImage = lockfile.getImage().get(Architecture.AARCH64);
        final UnchangingContainerReference x86Image = lockfile.getImage().get(Architecture.X86_64);

        assertEquals(x86Image.getRepository(), "repo_x86");
        assertEquals(x86Image.getTag(), "tag_x86");
        assertEquals(x86Image.getDigest(), "digest_x86");
        assertEquals(x86.getPackages().size(), 1);
        assertEquals(x86.getPackages().get(0).getName(), "jq");
        assertEquals(x86.getPackages().get(0).getVersion(), "1.5");
        assertEquals(x86.getPackages().get(0).getRelease(), "12.el8");
        assertEquals(x86.getPackages().get(0).getArchitecture(), "x86_64");

        assertEquals(armImage.getRepository(), "repo_arm");
        assertEquals(armImage.getTag(), "tag_arm");
        assertEquals(armImage.getDigest(), "digest_arm");
        assertEquals(arm.getPackages().size(), 1);
        assertEquals(arm.getPackages().get(0).getName(), "jq");
        assertEquals(arm.getPackages().get(0).getVersion(), "1.5");
        assertEquals(arm.getPackages().get(0).getRelease(), "12.el8");
        assertEquals(arm.getPackages().get(0).getArchitecture(), "aarch64");
    }
}
