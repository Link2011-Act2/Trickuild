package dev.trickuild.core

enum class ReleaseChannel { INT_DEV, BETA, RC, RELEASE, OTHER }

@ConsistentCopyVisibility
data class ParsedVersion internal constructor(
    val major: String,
    val minor: String,
    val patch: String?,
    val channel: ReleaseChannel,
    val qualifier: String?,
    val channelSuffix: String?,
    val buildMetadata: String?,
) {
    val numericVersion: String get() = listOfNotNull(major, minor, patch).joinToString(".")
    val majorMinor: String get() = "$major.$minor"
    val buildNumberPrefix: String get() = if (channel == ReleaseChannel.INT_DEV) "d" else "v"
}

class InvalidVersionException(version: String, reason: String) :
    IllegalArgumentException("Invalid versionName '$version': $reason. Expected Major.Minor[.Patch][-channel][+build], e.g. 2.2.0-IntDev_rev0.")

object VersionParser {
    private val pattern = Regex(
        """(0|[1-9][0-9]*)\.(0|[1-9][0-9]*)(?:\.(0|[1-9][0-9]*))?(?:-([A-Za-z0-9][A-Za-z0-9._-]*))?(?:\+([A-Za-z0-9][A-Za-z0-9._-]*))?""",
    )

    fun parse(value: String): ParsedVersion {
        fun invalid(reason: String): Nothing = throw InvalidVersionException(value, reason)
        if (value.isEmpty()) invalid("the value must not be empty")
        if (value.any(Char::isWhitespace)) invalid("whitespace is not allowed")
        if (value.startsWith("v")) invalid("omit the leading 'v'")
        val parts = value.substringBefore('-').substringBefore('+').split('.')
        if (parts.size !in 2..3) invalid("provide two or three numeric components")
        parts.forEachIndexed { index, part ->
            if (part.isEmpty() || part.any { it !in '0'..'9' }) {
                invalid("component ${index + 1} must be a non-negative integer")
            }
            if (part.length > 1 && part.startsWith('0')) invalid("component ${index + 1} has a leading zero")
        }
        val match = pattern.matchEntire(value)
            ?: invalid("channel/build suffixes must be non-empty, start with an ASCII letter or digit, and use only letters, digits, '.', '_' or '-'")
        val qualifier = match.groups[4]?.value
        val channelName = listOf("IntDev", "Beta", "RC", "Release")
            .firstOrNull { qualifier?.startsWith(it, ignoreCase = true) == true }
        val channel = when (channelName) {
            "IntDev" -> ReleaseChannel.INT_DEV
            "Beta" -> ReleaseChannel.BETA
            "RC" -> ReleaseChannel.RC
            "Release" -> ReleaseChannel.RELEASE
            else -> if (qualifier == null) ReleaseChannel.RELEASE else ReleaseChannel.OTHER
        }
        return ParsedVersion(
            match.groupValues[1], match.groupValues[2], match.groups[3]?.value,
            channel, qualifier, channelName?.let { qualifier!!.substring(it.length) },
            match.groups[5]?.value,
        )
    }
}
