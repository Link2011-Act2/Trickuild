package dev.trickuild.gradle

import dev.trickuild.core.TrickuildConfiguration
import org.gradle.api.DefaultTask
import org.gradle.api.provider.Property
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.TaskAction
import org.gradle.work.DisableCachingByDefault

@DisableCachingByDefault(because = "This diagnostic task only logs configuration and has no output files.")
abstract class TrickuildInfoTask : DefaultTask() {
    @get:Input
    abstract val enabledState: Property<Boolean>

    @get:Input
    abstract val codename: Property<String>

    @TaskAction
    fun showConfiguration() {
        logger.lifecycle(TrickuildConfiguration(enabledState.get()).statusMessage())
        logger.lifecycle("Trickuild codename: ${codename.get()}")
    }
}
