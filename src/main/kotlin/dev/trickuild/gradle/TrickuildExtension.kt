package dev.trickuild.gradle

import javax.inject.Inject
import org.gradle.api.Action
import org.gradle.api.file.ConfigurableFileCollection
import org.gradle.api.model.ObjectFactory
import org.gradle.api.provider.Property

abstract class TrickuildExtension @Inject constructor(objects: ObjectFactory) {
    abstract val enabled: Property<Boolean>
    abstract val codename: Property<String>
    abstract val versionName: Property<String>
    abstract val metadataPackage: Property<String>
    abstract val metadataClassName: Property<String>

    val buildNumber: BuildNumberExtension = objects.newInstance(BuildNumberExtension::class.java)
    val roulette: RouletteExtension = objects.newInstance(RouletteExtension::class.java)

    fun buildNumber(action: Action<in BuildNumberExtension>) = action.execute(buildNumber)
    fun roulette(action: Action<in RouletteExtension>) = action.execute(roulette)
}

abstract class BuildNumberExtension {
    abstract val enabled: Property<Boolean>
    abstract val hashLength: Property<Int>
    abstract val timestampSource: Property<String>
    abstract val timeZone: Property<String>
    abstract val sourceFiles: ConfigurableFileCollection
}

abstract class RouletteExtension {
    abstract val enabled: Property<Boolean>
    abstract val namespace: Property<String>
}
