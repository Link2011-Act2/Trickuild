package dev.trickuild.core

import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

object BuildNumberGenerator {
    fun generate(
        codename: String,
        version: ParsedVersion,
        instant: Instant,
        timeZone: ZoneId,
        contentHash: String,
    ): String {
        require(codename.isNotBlank() && codename.none(Char::isISOControl)) {
            "trickuild.codename must not be blank or contain control characters"
        }
        require(contentHash.matches(Regex("[0-9a-f]{1,64}"))) { "contentHash must contain 1..64 lowercase SHA-256 hex digits" }
        val timestamp = DateTimeFormatter.ofPattern("yyMMdd-HHmm", Locale.ROOT).withZone(timeZone).format(instant)
        return "$codename-${version.buildNumberPrefix}${version.numericVersion}-$timestamp-$contentHash"
    }
}
