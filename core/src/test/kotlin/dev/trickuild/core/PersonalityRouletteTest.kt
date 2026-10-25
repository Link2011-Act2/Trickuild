package dev.trickuild.core

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNotEquals

class PersonalityRouletteTest {
    @Test
    fun `known Glyph Barty rolls match including unsigned byte 229`() {
        mapOf("2.2" to 4, "2.10" to 5, "4.3" to 5).forEach { (version, expected) ->
            val roll = PersonalityRoulette.roll("GlyphBarty", VersionParser.parse(version))
            assertEquals("GlyphBarty:$version", roll.seed)
            assertEquals(expected, roll.number)
        }
        assertEquals(Personality.VIVACIOUS, PersonalityRoulette.roll("GlyphBarty", VersionParser.parse("2.2")).personality)
        assertEquals(listOf("Innocent", "Mad", "Composed", "Vivacious", "Depressed"), Personality.entries.map { it.displayName })
        assertEquals(listOf("純粋", "狂気", "冷静", "活発", "憂鬱"), Personality.entries.map { it.japaneseName })
    }

    @Test
    fun `patch and channel do not affect a roll but 2_10 is a distinct seed`() {
        val expected = PersonalityRoulette.roll("GlyphBarty", VersionParser.parse("2.2"))
        listOf("2.2.0", "2.2.9", "2.2.0-IntDev_rev0", "2.2.1-Beta1", "2.2.9+build.123").forEach {
            assertEquals(expected, PersonalityRoulette.roll("GlyphBarty", VersionParser.parse(it)))
        }
        assertNotEquals(
            PersonalityRoulette.roll("GlyphBarty", VersionParser.parse("2.1")).seed,
            PersonalityRoulette.roll("GlyphBarty", VersionParser.parse("2.10")).seed,
        )
        assertFailsWith<IllegalArgumentException> { PersonalityRoulette.roll(" ", VersionParser.parse("2.2")) }
    }
}
