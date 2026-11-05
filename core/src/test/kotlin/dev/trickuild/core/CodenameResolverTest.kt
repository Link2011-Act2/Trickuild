package dev.trickuild.core

import kotlin.test.Test
import kotlin.test.assertContains
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class CodenameResolverTest {
    @Test
    fun `missing empty and whitespace names use the default fallback`() {
        listOf(null, "", " ", "\t\n", "　").forEach {
            assertEquals("Youngchun", CodenameResolver.resolve(it))
        }
    }

    @Test
    fun `a custom fallback is used only when the codename is blank`() {
        listOf(null, "", " ").forEach {
            assertEquals("Unnamed", CodenameResolver.resolve(it, "Unnamed"))
        }
        assertEquals("Epica", CodenameResolver.resolve("Epica"))
        assertEquals("Epica", CodenameResolver.resolve("Epica", "Unnamed"))
        assertEquals("  Epica  ", CodenameResolver.resolve("  Epica  ", "Unnamed"))
    }

    @Test
    fun `invalid fallback is a configuration error even with an explicit codename`() {
        listOf("", " ", "\t\n", "　", "Name\n").forEach { fallback ->
            listOf(null, "Epica").forEach { codename ->
                val error = assertFailsWith<IllegalArgumentException> {
                    CodenameResolver.resolve(codename, fallback)
                }
                assertContains(error.message.orEmpty(), "trickuild.fallbackCodename must not be blank or contain control characters")
            }
        }
    }

    @Test
    fun `an invalid explicit codename does not silently use the fallback`() {
        val error = assertFailsWith<IllegalArgumentException> {
            CodenameResolver.resolve("Epica\n", "Unnamed")
        }
        assertContains(error.message.orEmpty(), "trickuild.codename")
    }
}
