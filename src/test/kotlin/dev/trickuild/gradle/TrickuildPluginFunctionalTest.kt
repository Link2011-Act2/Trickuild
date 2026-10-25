package dev.trickuild.gradle

import java.nio.file.Path
import kotlin.io.path.writeText
import kotlin.test.Test
import kotlin.test.assertContains
import kotlin.test.assertEquals
import org.gradle.testkit.runner.GradleRunner
import org.gradle.testkit.runner.TaskOutcome
import org.junit.jupiter.api.io.TempDir

class TrickuildPluginFunctionalTest {
    @TempDir
    lateinit var projectDir: Path

    @Test
    fun `plugin runs in a plain build without an Android SDK`() {
        writeBuild("")

        val result = runner().build()

        assertEquals(TaskOutcome.SUCCESS, result.task(":trickuildInfo")?.outcome)
        assertContains(result.output, "Trickuild enabled: true")
    }

    @Test
    fun `Kotlin DSL configuration survives configuration cache reuse`() {
        writeBuild("trickuild { enabled.set(false) }")

        val first = runner().build()
        assertContains(first.output, "Trickuild enabled: false")
        val second = runner().build()

        assertEquals(TaskOutcome.SUCCESS, second.task(":trickuildInfo")?.outcome)
        assertContains(second.output, "Trickuild enabled: false")
        assertContains(second.output, "Configuration cache entry reused.")
    }

    private fun writeBuild(configuration: String) {
        projectDir.resolve("settings.gradle.kts").writeText("rootProject.name = \"consumer\"")
        projectDir.resolve("build.gradle.kts").writeText(
            """
            plugins { id("dev.trickuild") }
            $configuration
            """.trimIndent(),
        )
    }

    private fun runner(): GradleRunner = GradleRunner.create()
        .withProjectDir(projectDir.toFile())
        .withPluginClasspath()
        .withEnvironment(System.getenv().filterKeys {
            it.uppercase() !in setOf("ANDROID_HOME", "ANDROID_SDK_ROOT")
        })
        .withArguments("trickuildInfo", "--configuration-cache", "--stacktrace")
}
