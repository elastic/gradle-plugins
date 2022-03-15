package co.elastic.gradle.utils.docker

import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.io.TempDir
import org.gradle.api.DefaultTask
import org.gradle.api.Project
import org.gradle.api.provider.Property
import org.gradle.testfixtures.ProjectBuilder
import org.junit.jupiter.api.Test
import java.nio.file.Path

internal class ContainerImageProviderTaskTest {
    private var testProject: Project? = null
    @BeforeEach
    fun setUp(@TempDir tempDir: Path) {
        testProject = ProjectBuilder.builder()
            .withProjectDir(tempDir.toFile())
            .build()
    }

    abstract class BuildDockerImage : DefaultTask(),
        ContainerImageProviderTask {
        init {
            tag.convention("ubuntu:20.04@sha256:8ae9bafbb64f63a50caab98fd3a5e37b3eb837a3e0780b78e5218e63193961f9")
        }

        abstract override fun getTag(): Property<String>
    }

    @get:Test
    val tag: Unit
        get() {
            testProject!!.tasks.create("test", BuildDockerImage::class.java)
        }
}