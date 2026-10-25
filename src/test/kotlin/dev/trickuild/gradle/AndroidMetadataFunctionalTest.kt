package dev.trickuild.gradle

import java.nio.file.Files
import java.nio.file.Path
import java.util.Properties
import java.util.zip.ZipFile
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
import org.junit.jupiter.api.Tag
import org.junit.jupiter.api.io.TempDir

@Tag("android")
class AndroidMetadataFunctionalTest {
    @TempDir lateinit var projectDir: Path

    @Test
    fun `Android APKs contain matching metadata preserve versionCode and work with configuration cache`() {
        val sdk = Path.of(System.getProperty("trickuild.androidSdk"))
        assertTrue(sdk.resolve("platforms/android-36").exists(), "Set -Ptrickuild.androidSdk to an SDK with API 36 and build-tools 36")
        val fixture = Path.of(System.getProperty("trickuild.projectRoot")).resolve("consumers/android").toFile()
        fixture.walkTopDown().onEnter { it.name !in setOf("build", ".gradle", ".kotlin", ".idea") }.filter { it.isFile && it.name != "local.properties" }.forEach { file ->
            val target = projectDir.resolve(file.relativeTo(fixture).path)
            target.parent.createDirectories()
            Files.copy(file.toPath(), target)
        }
        Properties().apply { setProperty("sdk.dir", sdk.toAbsolutePath().toString()) }.also { props ->
            projectDir.resolve("local.properties").toFile().writer().use { props.store(it, null) }
        }
        runner("help").build()
        assertFalse(metadataPath("Debug").exists(), "Gradle configuration must not materialize a timestamp")
        val first = runner(":app:assembleDebug", ":app:assembleRelease").build()
        assertEquals(TaskOutcome.SUCCESS, first.task(":app:generateDebugTrickuildMetadata")?.outcome)
        val debug = metadata("Debug")
        val release = metadata("Release")
        assertEquals(debug.getProperty("buildInstant"), release.getProperty("buildInstant"))
        assertEquals(debug.getProperty("BUILD_NUMBER"), release.getProperty("BUILD_NUMBER"))
        assertEquals("GlyphBarty:2.2", debug.getProperty("PERSONALITY_SEED"))
        assertEquals("4", debug.getProperty("PERSONALITY_NUMBER"))
        assertEquals(5, debug.getProperty("contentHash").length)
        verifyApk(debugApk(), debug.getProperty("BUILD_NUMBER"))
        verifyApk(projectDir.resolve("app/build/outputs/apk/release/app-release-unsigned.apk"), release.getProperty("BUILD_NUMBER"))
        val apkMetadata = Files.readString(projectDir.resolve("app/build/outputs/apk/debug/output-metadata.json"))
        assertTrue(Regex("\"versionCode\"\\s*:\\s*42").containsMatchIn(apkMetadata))

        val second = runner(":app:assembleDebug", ":app:assembleRelease").build()
        assertContains(second.output, "Configuration cache entry reused.")
        assertNotEquals(debug.getProperty("buildInstant"), metadata("Debug").getProperty("buildInstant"))
        assertEquals(debug.getProperty("contentHash"), metadata("Debug").getProperty("contentHash"))
        assertEquals(metadata("Debug").getProperty("buildInstant"), metadata("Release").getProperty("buildInstant"))
        verifyApk(debugApk(), metadata("Debug").getProperty("BUILD_NUMBER"))

        Files.delete(debugApk())
        runner(":app:assembleDebug").build()
        verifyApk(debugApk(), metadata("Debug").getProperty("BUILD_NUMBER"))
        projectDir.resolve("app/src/main/java/dev/trickuild/consumer/MainActivity.java").toFile().appendText("\n// source change\n")
        val changed = runner(":app:assembleDebug").build()
        assertContains(changed.output, "Configuration cache entry reused.")
        assertNotEquals(debug.getProperty("contentHash"), metadata("Debug").getProperty("contentHash"))
        verifyApk(debugApk(), metadata("Debug").getProperty("BUILD_NUMBER"))

        runner(":app:assembleDebug", "-PconsumerVersion=2.10.1-Beta2").build()
        assertEquals("GlyphBarty:2.10", metadata("Debug").getProperty("PERSONALITY_SEED"))
        assertEquals("5", metadata("Debug").getProperty("PERSONALITY_NUMBER"))
        assertTrue(metadata("Debug").getProperty("BUILD_NUMBER").startsWith("Epica-v2.10.1-"))
        assertContains(Files.readString(projectDir.resolve("app/build/outputs/apk/debug/output-metadata.json")), "2.10.1-Beta2")
        verifyApk(debugApk(), metadata("Debug").getProperty("BUILD_NUMBER"))
    }

    private fun verifyApk(apk: Path, number: String) {
        assertTrue(apk.exists(), "Missing APK: $apk")
        ZipFile(apk.toFile()).use { zip ->
            assertTrue(zip.getEntry("AndroidManifest.xml") != null)
            val dexStrings = zip.entries().asSequence().filter { it.name.matches(Regex("classes[0-9]*\\.dex")) }
                .map { zip.getInputStream(it).use { stream -> stream.readBytes().toString(Charsets.ISO_8859_1) } }.joinToString()
            assertContains(dexStrings, number)
        }
    }

    private fun metadataPath(variant: String) = projectDir.resolve("app/build/trickuild/generate${variant}TrickuildMetadata/metadata.properties")
    private fun metadata(variant: String) = Properties().apply { metadataPath(variant).toFile().reader(Charsets.UTF_8).use { load(it) } }
    private fun debugApk() = projectDir.resolve("app/build/outputs/apk/debug/app-debug.apk")
    private fun runner(vararg args: String): GradleRunner {
        // TestKit injection has its own parent loader; inject AGP alongside the plugin under test.
        // Normal consumers resolve both plugins together through their plugins block.
        val metadata = Properties().apply {
            AndroidMetadataFunctionalTest::class.java.classLoader.getResourceAsStream("plugin-under-test-metadata.properties")!!.use { load(it) }
        }
        val pluginFiles = (metadata.getProperty("implementation-classpath") + java.io.File.pathSeparator +
            System.getProperty("trickuild.androidPluginClasspath")).split(java.io.File.pathSeparator).map { java.io.File(it) }
        val runner = GradleRunner.create().withProjectDir(projectDir.toFile()).withPluginClasspath(pluginFiles)
            .withArguments(*args, "-Ptrickuild.testkit=true", "-PconsumerRoulette=true",
                "-PconsumerAgp=${System.getProperty("trickuild.androidAgp")}", "--configuration-cache", "--stacktrace", "--max-workers=2")
        System.getProperty("trickuild.androidGradleHome").takeIf { it.isNotBlank() }?.let { runner.withGradleInstallation(Path.of(it).toFile()) }
        System.getProperty("trickuild.testKitDir").takeIf { it.isNotBlank() }?.let { runner.withTestKitDir(Path.of(it).toFile()) }
        return runner
    }
}
