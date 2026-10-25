package dev.trickuild.core

import java.nio.file.Path
import java.security.MessageDigest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import org.junit.jupiter.api.Tag

@Tag("reference")
class ReferenceCompatibilityTest {
    @Test
    fun `read-only comparison against the actual four apps`() {
        val parent = Path.of(System.getProperty("trickuild.referenceRoot"))
        listOf("glyphvisualizer", "shutup", "schedulernittc", "beastlocator").forEach { name ->
            val root = parent.resolve(name).toFile()
            val module = root.resolve("app")
            assertTrue(module.isDirectory, "Missing reference app: $module")
            // The build supplies the exact FileTree selection used by the reference scripts.
            val encodedPaths = java.nio.file.Files.readString(Path.of(System.getProperty("trickuild.referenceFiles.$name")))
            val files = encodedPaths.split(',').filter { it.isNotEmpty() }
                .map { root.resolve(String(java.util.Base64.getDecoder().decode(it), Charsets.UTF_8)) }
                .sortedBy { it.relativeTo(root).invariantSeparatorsPath }
            val digest = MessageDigest.getInstance("SHA-256")
            files.forEach {
                digest.update(it.relativeTo(root).invariantSeparatorsPath.toByteArray(Charsets.UTF_8))
                digest.update(0.toByte())
                digest.update(it.readBytes())
                digest.update(0.toByte())
            }
            val legacyHash = digest.digest().joinToString("") { "%02x".format(it) }
            val actual = SourceContentHash.compute(root.toPath(), files.map { it.toPath() }, 64)
            assertEquals(legacyHash, actual, name)
            println("$name: ${files.size} files, SHA-256=$actual, default=${actual.take(5)}")
        }
    }
}
