package dev.trickuild.core

import java.security.MessageDigest

enum class Personality(val number: Int, val displayName: String, val japaneseName: String) {
    INNOCENT(1, "Innocent", "純粋"),
    MAD(2, "Mad", "狂気"),
    COMPOSED(3, "Composed", "冷静"),
    VIVACIOUS(4, "Vivacious", "活発"),
    DEPRESSED(5, "Depressed", "憂鬱"),
}

data class PersonalityRoll(val seed: String, val personality: Personality) {
    val number: Int get() = personality.number
}

object PersonalityRoulette {
    fun roll(namespace: String, version: ParsedVersion): PersonalityRoll {
        require(namespace.isNotBlank()) { "roulette.namespace must not be blank when roulette is enabled" }
        val seed = "$namespace:${version.majorMinor}"
        val firstByte = MessageDigest.getInstance("SHA-256").digest(seed.toByteArray(Charsets.UTF_8))[0].toInt() and 0xff
        return PersonalityRoll(seed, Personality.entries[firstByte % 5])
    }
}
