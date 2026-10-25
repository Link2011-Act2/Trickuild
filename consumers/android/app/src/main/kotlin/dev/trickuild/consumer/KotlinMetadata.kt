package dev.trickuild.consumer

/** Also exercises generated Java constants from AGP's built-in Kotlin compiler. */
object KotlinMetadata {
    @JvmStatic
    fun label(): String = "${TrickuildMetadata.CODE_NAME} / ${TrickuildMetadata.BUILD_NUMBER}"
}
