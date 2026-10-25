package dev.trickuild.gradle

import dev.trickuild.core.SourceContentHash
import java.net.URLClassLoader
import java.nio.file.Files
import java.nio.file.Path
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Properties
import kotlin.io.path.createDirectories
import kotlin.io.path.exists
import kotlin.io.path.writeText
import kotlin.test.Test
import kotlin.test.assertContains
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotEquals
import kotlin.test.assertTrue
import org.gradle.testkit.runner.GradleRunner
import org.gradle.testkit.runner.TaskOutcome
import org.junit.jupiter.api.io.TempDir

class MetadataFunctionalTest {
    @TempDir lateinit var projectDir: Path

    @Test
    fun `JVM artifacts use task execution time and refresh on cached configuration without requiring Android`() {
        writeConsumer()
        runner("help").build()
        assertFalse(metadataPath(projectDir).exists(), "Configuration/Sync must not generate metadata")
        val before = Instant.now()
        val first = runner("jar").build()
        val firstMetadata = metadata(projectDir)
        val instant = Instant.parse(firstMetadata.getProperty("buildInstant"))
        assertTrue(instant >= before && instant <= Instant.now())
        val date = DateTimeFormatter.ofPattern("yyMMdd-HHmm").withZone(ZoneId.of("UTC")).format(instant)
        assertEquals("Epica-d2.2.0-$date-${firstMetadata.getProperty("contentHash")}", firstMetadata.getProperty("BUILD_NUMBER"))
        assertEquals(5, firstMetadata.getProperty("contentHash").length)
        assertFalse(firstMetadata.containsKey("PERSONALITY_NUMBER"))
        assertEquals(TaskOutcome.SUCCESS, first.task(":compileJava")?.outcome)
        val expectedFiles = listOf("settings.gradle.kts", "build.gradle.kts", "src/main/java/example/UseMetadata.java").map { projectDir.resolve(it) }
        assertEquals(SourceContentHash.compute(projectDir, expectedFiles), firstMetadata.getProperty("contentHash"))
        URLClassLoader(arrayOf(projectDir.resolve("build/classes/java/main").toUri().toURL()), null).use { loader ->
            val generated = loader.loadClass("dev.trickuild.generated.TrickuildMetadata")
            assertEquals(firstMetadata.getProperty("BUILD_NUMBER"), generated.getField("BUILD_NUMBER").get(null))
            assertEquals("Epica", generated.getField("CODE_NAME").get(null))
        }

        val second = runner("jar").build()
        val secondMetadata = metadata(projectDir)
        assertContains(second.output, "Configuration cache entry reused.")
        assertEquals(TaskOutcome.SUCCESS, second.task(":generateTrickuildMetadata")?.outcome)
        assertNotEquals(firstMetadata.getProperty("buildInstant"), secondMetadata.getProperty("buildInstant"))
        assertEquals(firstMetadata.getProperty("contentHash"), secondMetadata.getProperty("contentHash"))

        projectDir.resolve("src/main/java/example/UseMetadata.java").toFile().appendText("\n// uncommitted source change\n")
        val changed = runner("jar").build()
        assertContains(changed.output, "Configuration cache entry reused.")
        assertNotEquals(firstMetadata.getProperty("contentHash"), metadata(projectDir).getProperty("contentHash"))

        // Adding custom inputs must retain the legacy defaults; setFrom is the replacement API.
        val extra = projectDir.resolve("extra.txt").also { it.writeText("first custom input") }
        projectDir.resolve("build.gradle.kts").toFile().appendText(
            "\ntrickuild.buildNumber.sourceFiles.from(layout.projectDirectory.file(\"extra.txt\"))\n",
        )
        runner("jar").build()
        val addedHash = metadata(projectDir).getProperty("contentHash")
        assertEquals(SourceContentHash.compute(projectDir, expectedFiles + listOf(extra)), addedHash)
        extra.writeText("changed custom input")
        runner("jar").build()
        assertNotEquals(addedHash, metadata(projectDir).getProperty("contentHash"))
    }

    @Test
    fun `roulette-only builds are incremental and match the 2_10 seed`() {
        writeConsumer()
        val arguments = arrayOf("jar", "-PbuildNumbers=false", "-Proulette=true", "-Pnamespace=GlyphBarty", "-PtestVersion=2.10.9-Beta2")
        runner(*arguments).build()
        val values = metadata(projectDir)
        assertEquals("GlyphBarty:2.10", values.getProperty("PERSONALITY_SEED"))
        assertEquals("5", values.getProperty("PERSONALITY_NUMBER"))
        assertFalse(values.containsKey("BUILD_NUMBER"))
        assertFalse(values.containsKey("buildInstant"))
        val second = runner(*arguments).build()
        assertContains(second.output, "Configuration cache entry reused.")
        assertEquals(TaskOutcome.UP_TO_DATE, second.task(":generateTrickuildMetadata")?.outcome)
        assertEquals(TaskOutcome.UP_TO_DATE, second.task(":compileJava")?.outcome)
        assertEquals(TaskOutcome.UP_TO_DATE, second.task(":jar")?.outcome)
        assertEquals(values, metadata(projectDir))
    }

    @Test
    fun `disabled features require neither game settings nor a version and clear stale fields`() {
        writeConsumer()
        runner("jar", "-Proulette=true", "-Pnamespace=GlyphBarty").build()
        assertEquals("4", metadata(projectDir).getProperty("PERSONALITY_NUMBER"))
        projectDir.resolve("build.gradle.kts").writeText(
            """
            plugins { java; id("dev.trickuild") }
            trickuild { enabled.set(false) }
            """.trimIndent(),
        )
        runner("jar").build()
        val values = metadata(projectDir)
        assertEquals("", values.getProperty("CODE_NAME"))
        assertFalse(values.containsKey("BUILD_NUMBER"))
        assertFalse(values.containsKey("PERSONALITY_SEED"))
        assertFalse(Files.readString(projectDir.resolve("build/generated/trickuild/generateTrickuildMetadata/java/TrickuildMetadata.java")).contains("PERSONALITY"))
        assertEquals(TaskOutcome.UP_TO_DATE, runner("jar").build().task(":generateTrickuildMetadata")?.outcome)
    }

    @Test
    fun `invalid version and missing roulette namespace have actionable diagnostics`() {
        writeConsumer()
        assertContains(runner("jar", "-PtestVersion=2.02.0").buildAndFail().output, "component 2 has a leading zero")
        assertContains(runner("jar", "-Proulette=true").buildAndFail().output, "roulette.namespace is required")
        assertContains(runner("jar", "-PhashLength=0").buildAndFail().output, "hashLength must be between 1 and 64")
        assertContains(runner("jar", "-PtimeZone=No/SuchZone").buildAndFail().output, "invalid buildNumber.timeZone")
        assertContains(runner("jar", "-PtimestampSource=source").buildAndFail().output, "timestampSource only supports 'build'")
    }

    @Test
    fun `parallel projects share one execution instant also after configuration cache reuse`() {
        projectDir.resolve("settings.gradle.kts").writeText("rootProject.name = \"parallel\"\ninclude(\":first\", \":second\")")
        projectDir.resolve("build.gradle.kts").writeText("")
        listOf("first", "second").forEach { name ->
            projectDir.resolve(name).createDirectories().resolve("build.gradle.kts").writeText(consumerScript)
        }
        repeat(2) {
            val result = runner(":first:jar", ":second:jar", "--parallel").build()
            if (it == 1) assertContains(result.output, "Configuration cache entry reused.")
            assertEquals(metadata(projectDir.resolve("first")).getProperty("buildInstant"), metadata(projectDir.resolve("second")).getProperty("buildInstant"))
        }
    }

    private fun writeConsumer() {
        projectDir.resolve("settings.gradle.kts").writeText("rootProject.name = \"jvm-consumer\"")
        projectDir.resolve("build.gradle.kts").writeText(consumerScript)
        projectDir.resolve("src/main/java/example/UseMetadata.java").also { it.parent.createDirectories() }.writeText(
            "package example; public class UseMetadata { public String code() { return dev.trickuild.generated.TrickuildMetadata.CODE_NAME; } }",
        )
        projectDir.resolve("src/build/ignored.txt").also { it.parent.createDirectories() }.writeText("excluded build directory")
        projectDir.resolve("src/.git/objects/ignored").also { it.parent.createDirectories() }.writeText("excluded Git data")
        projectDir.resolve("unrelated.txt").writeText("outside src")
    }

    private fun metadataPath(root: Path) = root.resolve("build/trickuild/generateTrickuildMetadata/metadata.properties")
    private fun metadata(root: Path) = Properties().apply { metadataPath(root).toFile().reader(Charsets.UTF_8).use { load(it) } }
    private fun runner(vararg arguments: String): GradleRunner = GradleRunner.create()
        .withProjectDir(projectDir.toFile()).withPluginClasspath()
        .withEnvironment(System.getenv().filterKeys { it.uppercase() !in setOf("ANDROID_HOME", "ANDROID_SDK_ROOT") })
        .withArguments(*arguments, "--configuration-cache", "--stacktrace", "--max-workers=2")

    private val consumerScript = """
        plugins { java; id("dev.trickuild") }
        trickuild {
            codename.set("Epica")
            versionName.set(providers.gradleProperty("testVersion").orElse("2.2.0-IntDev_rev0"))
            buildNumber {
                enabled.set(providers.gradleProperty("buildNumbers").map { it.toBoolean() }.orElse(true))
                hashLength.set(providers.gradleProperty("hashLength").map { it.toInt() }.orElse(5))
                timeZone.set(providers.gradleProperty("timeZone").orElse("UTC"))
                timestampSource.set(providers.gradleProperty("timestampSource").orElse("build"))
            }
            roulette {
                enabled.set(providers.gradleProperty("roulette").map { it.toBoolean() }.orElse(false))
                namespace.set(providers.gradleProperty("namespace"))
            }
        }
    """.trimIndent()
}
