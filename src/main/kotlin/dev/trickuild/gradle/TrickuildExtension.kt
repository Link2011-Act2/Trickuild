package dev.trickuild.gradle

import org.gradle.api.provider.Property

abstract class TrickuildExtension {
    abstract val enabled: Property<Boolean>
}
