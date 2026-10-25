package dev.trickuild.gradle

import dev.trickuild.android.AndroidIntegration
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.api.plugins.JavaPluginExtension
import org.gradle.api.provider.Provider
import java.time.ZoneId

class TrickuildPlugin : Plugin<Project> {
    override fun apply(project: Project) {
        val extension = project.extensions.create("trickuild", TrickuildExtension::class.java)
        extension.enabled.convention(true)
        extension.metadataClassName.convention("TrickuildMetadata")
        extension.buildNumber.enabled.convention(true)
        extension.buildNumber.hashLength.convention(5)
        extension.buildNumber.timestampSource.convention("build")
        extension.buildNumber.timeZone.convention(ZoneId.systemDefault().id)
        extension.roulette.enabled.convention(false)
        extension.buildNumber.sourceFiles.from(
            project.fileTree("src") { exclude("**/build/**") },
            project.layout.projectDirectory.file("build.gradle.kts"),
            project.rootProject.layout.projectDirectory.file("build.gradle.kts"),
            project.rootProject.layout.projectDirectory.file("settings.gradle.kts"),
            project.rootProject.layout.projectDirectory.file("gradle/libs.versions.toml"),
        )
        val clock = project.gradle.sharedServices.registerIfAbsent("trickuild-build-clock", BuildClockService::class.java) {}

        fun registerMetadata(name: String, packageName: Provider<String>, versionName: Provider<String>) =
            project.tasks.register(name, GenerateTrickuildMetadataTask::class.java) {
                group = "trickuild"
                description = "Generates build metadata using the actual task execution time."
                enabledState.set(extension.enabled)
                buildNumberEnabled.set(extension.buildNumber.enabled)
                rouletteEnabled.set(extension.roulette.enabled)
                codename.set(extension.codename)
                this.versionName.set(versionName)
                rouletteNamespace.set(extension.roulette.namespace)
                hashLength.set(extension.buildNumber.hashLength)
                timeZone.set(extension.buildNumber.timeZone)
                timestampSource.set(extension.buildNumber.timestampSource)
                metadataPackage.set(extension.metadataPackage.orElse(packageName))
                metadataClassName.set(extension.metadataClassName)
                sourceFiles.from(extension.buildNumber.sourceFiles.filter { it.isFile })
                hashRootDirectory.set(project.rootProject.layout.projectDirectory)
                buildClock.set(clock)
                usesService(clock)
                outputDirectory.convention(project.layout.buildDirectory.dir("generated/trickuild/$name/java"))
                metadataFile.convention(project.layout.buildDirectory.file("trickuild/$name/metadata.properties"))
            }

        val standalone = registerMetadata("generateTrickuildMetadata", project.providers.provider { "dev.trickuild.generated" }, extension.versionName)
        project.pluginManager.withPlugin("java") {
            project.extensions.getByType(JavaPluginExtension::class.java).sourceSets.named("main") {
                java.srcDir(standalone.flatMap { it.outputDirectory })
            }
        }
        project.pluginManager.withPlugin("com.android.application") {
            AndroidIntegration.connect(project, extension.versionName) { variant, namespace, version ->
                val capitalized = variant.replaceFirstChar { it.uppercaseChar() }
                registerMetadata("generate${capitalized}TrickuildMetadata", namespace, version)
            }
        }

        project.tasks.register("trickuildInfo", TrickuildInfoTask::class.java) {
            group = "trickuild"
            description = "Displays the current Trickuild configuration."
            enabledState.set(extension.enabled)
        }
    }
}
