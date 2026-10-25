package dev.trickuild.core

import java.time.Instant
import java.time.ZoneId
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNotEquals

class BuildNumberGeneratorTest {
    private val instant = Instant.parse("2026-10-25T10:20:00Z")

    @Test
    fun `fixed build time and timezone produce the specified format`() {
        assertEquals("Epica-d2.2.0-261025-1920-a3f2b", number("2.2.0-IntDev_rev0"))
        assertEquals("Epica-d2.2-261025-1920-a3f2b", number("2.2-intdev"))
        listOf("2.2.0-Beta2", "2.2.0-RC1", "2.2.0-Release", "2.2.0", "2.2.0+ci.123").forEach {
            assertEquals("Epica-v2.2.0-261025-1920-a3f2b", number(it))
        }
        assertEquals("Epica-v2.10.0-261025-1920-a3f2b", number("2.10.0"))
        assertEquals("Epica-v2.2.0-261025-1020-a3f2b", number("2.2.0", zone = "UTC"))
    }

    @Test
    fun `different build execution minutes change the number without changing the hash`() {
        val later = number("2.2.0", time = instant.plusSeconds(60))
        assertEquals("Epica-v2.2.0-261025-1921-a3f2b", later)
        assertNotEquals(number("2.2.0"), later)
    }

    @Test
    fun `invalid codenames and hash values fail clearly`() {
        assertFailsWith<IllegalArgumentException> {
            BuildNumberGenerator.generate("", VersionParser.parse("2.2"), instant, ZoneId.of("UTC"), "a3f2b")
        }
        assertFailsWith<IllegalArgumentException> {
            BuildNumberGenerator.generate("Epica", VersionParser.parse("2.2"), instant, ZoneId.of("UTC"), "not-a-hash")
        }
    }

    private fun number(version: String, time: Instant = instant, zone: String = "Asia/Tokyo") =
        BuildNumberGenerator.generate("Epica", VersionParser.parse(version), time, ZoneId.of(zone), "a3f2b")
}
