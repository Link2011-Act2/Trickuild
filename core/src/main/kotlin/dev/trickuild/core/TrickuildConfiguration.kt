package dev.trickuild.core

/** Plain Kotlin configuration shared by future integrations. */
data class TrickuildConfiguration(val enabled: Boolean = true) {
    fun statusMessage(): String = "Trickuild enabled: $enabled"
}
