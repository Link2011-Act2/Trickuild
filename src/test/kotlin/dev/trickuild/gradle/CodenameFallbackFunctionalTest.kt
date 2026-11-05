package dev.trickuild.gradle

import java.net.URLClassLoader
import java.nio.file.Path
import java.util.Properties
import kotlin.io.path.createDirectories
import kotlin.io.path.writeText
import kotlin.test.Test
import kotlin.test.assertContains
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import org.gradle.testkit.runner.GradleRunner
import org.junit.jupiter.api.io.TempDir

class CodenameFallbackFunctionalTest {
    @TempDir lateinit var projectDir: Path

    @Test
    fun `unset codename uses Youngchun also after configuration cache reuse`() =
        assertCodename("", "Youngchun", reuse = true)

    @Test
    fun `empty codename uses Youngchun`() =
        assertCodename("codename.set(\"\")", "Youngchun")

    @Test
    fun `whitespace codename uses Youngchun`() =
        assertCodename("codename.set(\"  \\u3000\")", "Youngchun")

    @Test
    fun `explicit codename is preserved`() =
        assertCodename("codename.set(\"Epica\")", "Epica")

    @Test
    fun `custom fallback survives configuration cache reuse`() =
        assertCodename("fallbackCodename.set(\"Unnamed\")", "Unnamed", reuse = true)

    @Test
    fun `explicit codename wins over a custom fallback`() =
        assertCodename("codename.set(\"Epica\"); fallbackCodename.set(\"Unnamed\")", "Epica")

    @Test
    fun `absent providers and null values are safely resolved`() = assertCodename(
        """
        codename.set(null as String?)
        fallbackCodename.set(null as String?)
        codename.set(providers.gradleProperty("missingCodename"))
        fallbackCodename.set(providers.gradleProperty("missingFallbackCodename"))
        """.trimIndent(),
        "Youngchun", reuse = true,
    )

    @Test
    fun `blank fallback fails generation and diagnostics with an actionable configuration error`() {
        listOf("", "  \\u3000").forEach { fallback ->
            writeConsumer("codename.set(\"Epica\"); fallbackCodename.set(\"$fallback\")")
            listOf("generateTrickuildMetadata", "trickuildInfo").forEach { task ->
                val failure = runner(task).buildAndFail()
                assertContains(failure.output, "trickuild.fallbackCodename must not be blank")
                assertContains(failure.output, "leave it unset to use 'Youngchun'")
            }
        }
    }

    @Test
    fun `roulette is optional and its result is independent of codename and fallback`() {
        writeConsumer("fallbackCodename.set(\"Unnamed\")")
        runner("jar").build()
        assertMetadata("Unnamed").also { assertFalse(it.containsKey("PERSONALITY_NUMBER")) }

        runner("jar", "-PtestRoulette=true").build()
        val first = assertMetadata("Unnamed")
        assertEquals("IndependentConsumer:1.0", first.getProperty("PERSONALITY_SEED"))
        assertTrue(first.getProperty("PERSONALITY_NUMBER").toInt() in 1..5)

        writeConsumer("codename.set(\"Epica\"); fallbackCodename.set(\"Different\")")
        runner("jar", "-PtestRoulette=true").build()
        val second = assertMetadata("Epica")
        listOf("PERSONALITY_SEED", "PERSONALITY_NUMBER", "personality", "personalityJapanese").forEach {
            assertEquals(first.getProperty(it), second.getProperty(it))
        }
    }

    private fun assertCodename(configuration: String, expected: String, reuse: Boolean = false) {
        writeConsumer(configuration)
        repeat(if (reuse) 2 else 1) { attempt ->
            val result = runner("jar", "trickuildInfo").build()
            if (attempt > 0) assertContains(result.output, "Configuration cache entry reused.")
            assertContains(result.output, "Trickuild codename: $expected")
            assertMetadata(expected).also { assertFalse(it.containsKey("PERSONALITY_NUMBER")) }
        }
    }

    private fun assertMetadata(expected: String): Properties {
        val values = Properties().apply {
            projectDir.resolve("build/trickuild/generateTrickuildMetadata/metadata.properties")
                .toFile().reader(Charsets.UTF_8).use { load(it) }
        }
        assertEquals(expected, values.getProperty("CODE_NAME"))
        assertTrue(values.getProperty("BUILD_NUMBER").startsWith("$expected-v1.0.0-"))
        assertEquals(5, values.getProperty("contentHash").length)
        URLClassLoader(arrayOf(projectDir.resolve("build/classes/java/main").toUri().toURL()), null).use { loader ->
            val generated = loader.loadClass("dev.trickuild.generated.TrickuildMetadata")
            assertEquals(expected, generated.getField("CODE_NAME").get(null))
            assertEquals(values.getProperty("BUILD_NUMBER"), generated.getField("BUILD_NUMBER").get(null))
        }
        return values
    }

    private fun writeConsumer(configuration: String) {
        projectDir.resolve("settings.gradle.kts").writeText("rootProject.name = \"codename-consumer\"")
        projectDir.resolve("build.gradle.kts").writeText(
            """
            plugins { java; id("dev.trickuild") }
            trickuild {
                versionName.set("1.0.0")
                buildNumber.timeZone.set("UTC")
                roulette {
                    enabled.set(providers.gradleProperty("testRoulette").map { it.toBoolean() }.orElse(false))
                    namespace.set("IndependentConsumer")
                }
                $configuration
            }
            """.trimIndent(),
        )
        projectDir.resolve("src/main/java/example/UseMetadata.java").also { it.parent.createDirectories() }.writeText(
            "package example; public class UseMetadata { public String code() { return dev.trickuild.generated.TrickuildMetadata.CODE_NAME; } }",
        )
    }

    private fun runner(vararg arguments: String) = GradleRunner.create()
        .withProjectDir(projectDir.toFile())
        .withPluginClasspath()
        .withEnvironment(System.getenv().filterKeys { it.uppercase() !in setOf("ANDROID_HOME", "ANDROID_SDK_ROOT") })
        .withArguments(*arguments, "--configuration-cache", "--stacktrace", "--max-workers=2")
}
