package dev.trickuild.core

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNotEquals
import kotlin.test.assertTrue

class VersionParserTest {
    @Test
    fun `separates numeric components channels qualifiers and build metadata`() {
        val version = VersionParser.parse("2.2.0-IntDev_rev0+ci.123")
        assertEquals("2", version.major)
        assertEquals("2", version.minor)
        assertEquals("0", version.patch)
        assertEquals("2.2.0", version.numericVersion)
        assertEquals(ReleaseChannel.INT_DEV, version.channel)
        assertEquals("IntDev_rev0", version.qualifier)
        assertEquals("_rev0", version.channelSuffix)
        assertEquals("ci.123", version.buildMetadata)
        assertEquals("2", VersionParser.parse("2.2.1-Beta2").channelSuffix)
        assertEquals(ReleaseChannel.BETA, VersionParser.parse("2.2.1-Beta2").channel)
        assertEquals(ReleaseChannel.RC, VersionParser.parse("3.0.0-RC1").channel)
        assertEquals(ReleaseChannel.RELEASE, VersionParser.parse("4.3.0").channel)
        assertEquals(ReleaseChannel.RELEASE, VersionParser.parse("4.3-Release").channel)
        assertEquals(ReleaseChannel.OTHER, VersionParser.parse("4.3-Custom_1").channel)
        assertEquals(null, VersionParser.parse("2.10").patch)
    }

    @Test
    fun `keeps minor components as integer strings including very large numbers`() {
        assertEquals("10", VersionParser.parse("2.10.0").minor)
        assertNotEquals(VersionParser.parse("2.1").majorMinor, VersionParser.parse("2.10").majorMinor)
        assertEquals("12345678901234567890", VersionParser.parse("2.12345678901234567890").minor)
    }

    @Test
    fun `invalid versions explain the cause`() {
        val cases = mapOf(
            "" to "empty", "2" to "two or three", "2.2.0.1" to "two or three",
            "v2.2.0" to "leading 'v'", "2.x.0" to "non-negative integer",
            "2.02.0" to "leading zero", "2.2.00" to "leading zero", "-1.2.0" to "two or three",
            " 2.2.0" to "whitespace", "2.2.0\n" to "whitespace",
            "2.2.0-" to "suffixes", "2.2.0+" to "suffixes", "2.2.0-?" to "suffixes",
            "2.2.0+a+b" to "suffixes", "2..0" to "non-negative integer",
        )
        cases.forEach { (input, reason) ->
            val error = assertFailsWith<InvalidVersionException>(input) { VersionParser.parse(input) }
            assertTrue(error.message.orEmpty().contains(reason), error.message)
        }
    }
}
