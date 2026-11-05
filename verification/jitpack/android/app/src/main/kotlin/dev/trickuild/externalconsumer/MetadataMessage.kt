package dev.trickuild.externalconsumer

object MetadataMessage {
    @JvmStatic
    fun render(): String = "${TrickuildMetadata.CODE_NAME} / ${TrickuildMetadata.BUILD_NUMBER}"
}
