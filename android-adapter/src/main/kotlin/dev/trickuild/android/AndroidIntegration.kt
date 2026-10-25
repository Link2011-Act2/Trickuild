package dev.trickuild.android

import com.android.build.api.variant.ApplicationAndroidComponentsExtension
import org.gradle.api.Project
import org.gradle.api.Task
import org.gradle.api.file.DirectoryProperty
import org.gradle.api.provider.Provider
import org.gradle.api.tasks.TaskProvider

interface GeneratedMetadataOutput {
    val outputDirectory: DirectoryProperty
}

/** This class is loaded only after com.android.application is present. */
object AndroidIntegration {
    fun <T> connect(
        project: Project,
        versionOverride: Provider<String>,
        register: (String, Provider<String>, Provider<String>) -> TaskProvider<T>,
    ) where T : Task, T : GeneratedMetadataOutput {
        val components = project.extensions.getByType(ApplicationAndroidComponentsExtension::class.java)
        components.onVariants(components.selector().all()) { variant ->
            if (versionOverride.isPresent) {
                variant.outputs.forEach { it.versionName.set(versionOverride) }
            }
            val task = register(variant.name, variant.namespace, variant.outputs.first().versionName)
            variant.sources.java?.addGeneratedSourceDirectory(task) { it.outputDirectory }
                ?: error("Trickuild requires the Java Sources API for Android variant '${variant.name}'")
        }
    }
}
