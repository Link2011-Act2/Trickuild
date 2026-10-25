package dev.trickuild.core

import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.attribute.FileTime
import kotlin.io.path.createDirectories
import kotlin.io.path.writeText
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNotEquals
import org.junit.jupiter.api.io.TempDir

class SourceContentHashTest {
    @TempDir lateinit var directory: Path

    @Test
    fun `four app layouts match the legacy golden digest including UTF8 paths and raw CRLF bytes`() {
        listOf("GlyphBarty", "ShutUp", "NITTC_Scheduler", "BeastLocator").forEach { name ->
            val root = directory.resolve(name).createDirectories()
            fixture.forEach { (path, contents) ->
                root.resolve(path).also { it.parent.createDirectories() }.writeText(contents)
            }
            root.resolve("app/src/build/ignored.kt").also { it.parent.createDirectories() }.writeText("ignored")
            root.resolve("unrelated.txt").writeText("ignored")
            val files = fixture.keys.map { root.resolve(it) }
            assertEquals(6, files.size)
            assertEquals("6ef03df6b85c7a3fed961395de261d58a4d8042718e2ae9a510a9baa928703ed", SourceContentHash.compute(root, files, 64), name)
            assertEquals("6ef03", SourceContentHash.compute(root, files), name)
            assertEquals(SourceContentHash.compute(root, files), SourceContentHash.compute(root, files.reversed() + files.first()), name)
        }
    }

    @Test
    fun `only contents and relative paths matter and empty files and delimiters are included`() {
        val file = directory.resolve("code.kt").also { it.writeText("first") }
        val original = SourceContentHash.compute(directory, listOf(file))
        Files.setLastModifiedTime(file, FileTime.fromMillis(1L))
        assertEquals(original, SourceContentHash.compute(directory, listOf(file)))
        file.writeText("uncommitted change")
        val edited = SourceContentHash.compute(directory, listOf(file))
        assertNotEquals(original, edited)
        val renamed = directory.resolve("renamed.kt")
        Files.move(file, renamed)
        assertNotEquals(SourceContentHash.compute(directory, listOf(renamed)), edited)
        val empty = directory.resolve("empty.kt").also { it.writeText("") }
        assertNotEquals(SourceContentHash.compute(directory, listOf(renamed)), SourceContentHash.compute(directory, listOf(renamed, empty)))
        assertFailsWith<IllegalArgumentException> { SourceContentHash.compute(directory, listOf(renamed), 0) }
        assertFailsWith<IllegalArgumentException> { SourceContentHash.compute(directory, listOf(renamed), 65) }
        assertEquals(64, SourceContentHash.compute(directory, listOf(renamed), 64).length)
    }

    @Test
    fun `duplicate root module build script inputs are only hashed once`() {
        val script = directory.resolve("build.gradle.kts").also { it.writeText("// root") }
        assertEquals(SourceContentHash.compute(directory, listOf(script)), SourceContentHash.compute(directory, listOf(script, script)))
    }

    private val fixture = linkedMapOf(
        "app/src/main/Main.kt" to "package sample\n",
        "app/build.gradle.kts" to "// app\n",
        "build.gradle.kts" to "// root\n",
        "settings.gradle.kts" to "rootProject.name = \"fixture\"\n",
        "gradle/libs.versions.toml" to "[versions]\n",
        "app/src/main/res/values/名前.xml" to "<resources/>\r\n",
    )
}
