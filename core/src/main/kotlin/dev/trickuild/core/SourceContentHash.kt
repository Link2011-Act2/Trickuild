package dev.trickuild.core

import java.nio.file.Files
import java.nio.file.Path
import java.security.MessageDigest
import kotlin.io.path.invariantSeparatorsPathString

/** Compatible with the four reference apps: path, NUL, raw contents, NUL; no mtimes or Git. */
object SourceContentHash {
    fun compute(rootDirectory: Path, files: Iterable<Path>, length: Int = 5): String {
        require(length in 1..64) { "buildNumber.hashLength must be between 1 and 64 (was $length)" }
        val root = rootDirectory.toAbsolutePath().normalize()
        val paths = files.map { it.toAbsolutePath().normalize() }.distinct().filter(Files::isRegularFile)
        val entries = paths.map { path ->
            require(path.startsWith(root)) { "Hash input must be inside the root directory: $path" }
            root.relativize(path).invariantSeparatorsPathString to path
        }.sortedBy { it.first }
        val digest = MessageDigest.getInstance("SHA-256")
        entries.forEach { (relativePath, path) ->
            digest.update(relativePath.toByteArray(Charsets.UTF_8))
            digest.update(0.toByte())
            Files.newInputStream(path).use { stream ->
                val buffer = ByteArray(8192)
                var count = stream.read(buffer)
                while (count != -1) {
                    digest.update(buffer, 0, count)
                    count = stream.read(buffer)
                }
            }
            digest.update(0.toByte())
        }
        return digest.digest().joinToString("") { "%02x".format(it.toInt() and 0xff) }.take(length)
    }
}
