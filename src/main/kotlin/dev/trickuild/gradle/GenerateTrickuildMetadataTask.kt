package dev.trickuild.gradle

import dev.trickuild.android.GeneratedMetadataOutput
import dev.trickuild.core.BuildNumberGenerator
import dev.trickuild.core.PersonalityRoulette
import dev.trickuild.core.SourceContentHash
import dev.trickuild.core.VersionParser
import java.io.StringWriter
import java.time.DateTimeException
import java.time.ZoneId
import java.util.Properties
import org.gradle.api.DefaultTask
import org.gradle.api.GradleException
import org.gradle.api.file.ConfigurableFileCollection
import org.gradle.api.file.DirectoryProperty
import org.gradle.api.file.RegularFileProperty
import org.gradle.api.provider.Property
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.InputFiles
import org.gradle.api.tasks.Internal
import org.gradle.api.tasks.Optional
import org.gradle.api.tasks.OutputDirectory
import org.gradle.api.tasks.OutputFile
import org.gradle.api.tasks.PathSensitive
import org.gradle.api.tasks.PathSensitivity
import org.gradle.api.tasks.TaskAction
import org.gradle.work.DisableCachingByDefault

@DisableCachingByDefault(because = "Build numbers contain the time of this build execution, not a cacheable input timestamp.")
abstract class GenerateTrickuildMetadataTask : DefaultTask(), GeneratedMetadataOutput {
    @get:Input abstract val enabledState: Property<Boolean>
    @get:Input abstract val buildNumberEnabled: Property<Boolean>
    @get:Input abstract val rouletteEnabled: Property<Boolean>
    @get:Input @get:Optional abstract val codename: Property<String>
    @get:Input @get:Optional abstract val versionName: Property<String>
    @get:Input @get:Optional abstract val rouletteNamespace: Property<String>
    @get:Input abstract val hashLength: Property<Int>
    @get:Input abstract val timeZone: Property<String>
    @get:Input abstract val timestampSource: Property<String>
    @get:Input abstract val metadataPackage: Property<String>
    @get:Input abstract val metadataClassName: Property<String>
    @get:InputFiles @get:PathSensitive(PathSensitivity.RELATIVE)
    abstract val sourceFiles: ConfigurableFileCollection
    @get:Internal abstract val hashRootDirectory: DirectoryProperty
    @get:Internal abstract val buildClock: Property<BuildClockService>
    @get:OutputDirectory abstract override val outputDirectory: DirectoryProperty
    @get:OutputFile abstract val metadataFile: RegularFileProperty

    init {
        // Strict build-time semantics. Roulette-only/disabled metadata remains incremental.
        outputs.upToDateWhen { !enabledState.get() || !buildNumberEnabled.get() }
    }

    @TaskAction
    fun generate() {
        try {
            generateMetadata()
        } catch (error: IllegalArgumentException) {
            throw GradleException("Trickuild: ${error.message}", error)
        } catch (error: DateTimeException) {
            throw GradleException("Trickuild: invalid buildNumber.timeZone '${timeZone.get()}': ${error.message}", error)
        }
    }

    private fun generateMetadata() {
        val buildEnabled = enabledState.get() && buildNumberEnabled.get()
        val rollEnabled = enabledState.get() && rouletteEnabled.get()
        val name = codename.orNull.orEmpty()
        val version = if (buildEnabled || rollEnabled) {
            VersionParser.parse(versionName.orNull ?: errorMessage("versionName is required; set trickuild.versionName or Android defaultConfig.versionName"))
        } else null
        val fields = linkedMapOf<String, Any>("CODE_NAME" to name)
        val details = Properties()
        val instant = if (buildEnabled) {
            require(timestampSource.get() == "build") { "buildNumber.timestampSource only supports 'build'" }
            val zone = ZoneId.of(timeZone.get())
            val hash = SourceContentHash.compute(hashRootDirectory.get().asFile.toPath(), sourceFiles.files.map { it.toPath() }, hashLength.get())
            val now = buildClock.get().instant
            fields["BUILD_NUMBER"] = BuildNumberGenerator.generate(name, version!!, now, zone, hash)
            details.setProperty("contentHash", hash)
            details.setProperty("buildInstant", now.toString())
            now.toString()
        } else null
        if (rollEnabled) {
            val roll = PersonalityRoulette.roll(rouletteNamespace.orNull ?: errorMessage("roulette.namespace is required when roulette is enabled"), version!!)
            fields["PERSONALITY_SEED"] = roll.seed
            fields["PERSONALITY_NUMBER"] = roll.number
            details.setProperty("personality", roll.personality.displayName)
            details.setProperty("personalityJapanese", roll.personality.japaneseName)
        }
        versionName.orNull?.let { details.setProperty("versionName", it) }
        details.setProperty("metadataPackage", metadataPackage.get())
        details.setProperty("metadataClassName", metadataClassName.get())
        fields.forEach { (key, value) -> details.setProperty(key, value.toString()) }

        val source = JavaMetadataWriter.render(metadataPackage.get(), metadataClassName.get(), fields, instant)
        val directory = outputDirectory.get().asFile
        val propertiesFile = metadataFile.get().asFile
        // Remove only our previously named generated Java file when the class name changes.
        if (propertiesFile.isFile) {
            val previous = Properties().apply { propertiesFile.reader(Charsets.UTF_8).use { load(it) } }
            val oldClass = previous.getProperty("metadataClassName")
            if (oldClass != null && oldClass != metadataClassName.get() && oldClass.matches(Regex("[A-Za-z_$][A-Za-z0-9_$]*"))) {
                java.nio.file.Files.deleteIfExists(directory.toPath().resolve("$oldClass.java"))
            }
        }
        directory.mkdirs()
        directory.resolve("${metadataClassName.get()}.java").writeText(source, Charsets.UTF_8)
        propertiesFile.parentFile.mkdirs()
        val serialized = StringWriter().also { details.store(it, null) }.toString().lineSequence()
            .filter { it.isNotEmpty() && !it.startsWith('#') }.sorted().joinToString("\n", postfix = "\n")
        propertiesFile.writeText(serialized, Charsets.UTF_8)
    }

    private fun errorMessage(message: String): Nothing = throw IllegalArgumentException(message)
}
