package dev.trickuild.gradle

import org.gradle.api.Plugin
import org.gradle.api.Project

class TrickuildPlugin : Plugin<Project> {
    override fun apply(project: Project) {
        val extension = project.extensions.create("trickuild", TrickuildExtension::class.java)
        extension.enabled.convention(true)

        project.tasks.register("trickuildInfo", TrickuildInfoTask::class.java) {
            group = "trickuild"
            description = "Displays the current Trickuild configuration."
            enabledState.set(extension.enabled)
        }
    }
}
