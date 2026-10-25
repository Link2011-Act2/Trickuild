package dev.trickuild.core

import kotlin.test.Test
import kotlin.test.assertEquals

class TrickuildConfigurationTest {
    @Test
    fun `enabled by default`() {
        assertEquals("Trickuild enabled: true", TrickuildConfiguration().statusMessage())
    }

    @Test
    fun `disabled configuration is preserved`() {
        assertEquals("Trickuild enabled: false", TrickuildConfiguration(false).statusMessage())
    }
}
