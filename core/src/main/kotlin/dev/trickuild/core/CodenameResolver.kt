package dev.trickuild.core

/** Resolves a name without changing the caller's configured values. */
object CodenameResolver {
    const val DEFAULT_FALLBACK = "Youngchun"

    fun resolve(codename: String?, fallbackCodename: String? = null): String {
        val fallback = fallbackCodename ?: DEFAULT_FALLBACK
        require(fallback.isNotBlank() && fallback.none(Char::isISOControl)) {
            "trickuild.fallbackCodename must not be blank or contain control characters; " +
                "set a non-blank name or leave it unset to use '$DEFAULT_FALLBACK'"
        }
        val effective = codename?.takeUnless(String::isBlank) ?: fallback
        require(effective.none(Char::isISOControl)) {
            "trickuild.codename must not contain control characters"
        }
        return effective
    }
}
