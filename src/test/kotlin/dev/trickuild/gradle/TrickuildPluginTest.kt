package dev.trickuild.gradle

import org.gradle.testfixtures.ProjectBuilder
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class TrickuildPluginTest {
    @Test
    fun `registers the extension and lazily connected task without applying Android plugins`() {
        val project = ProjectBuilder.builder().build()
        project.pluginManager.apply("dev.trickuild")

        val extension = project.extensions.getByType(TrickuildExtension::class.java)
        val task = project.tasks.named("trickuildInfo", TrickuildInfoTask::class.java).get()
        assertTrue(extension.enabled.get())
        assertTrue(task.enabledState.get())

        extension.enabled.set(false)
        assertFalse(task.enabledState.get())
        assertFalse(project.pluginManager.hasPlugin("com.android.application"))
        assertFalse(project.pluginManager.hasPlugin("com.android.library"))
    }

    @Test
    fun `resolved task inputs track DSL changes without rewriting configured values`() {
        val project = ProjectBuilder.builder().build()
        project.pluginManager.apply("dev.trickuild")
        val extension = project.extensions.getByType(TrickuildExtension::class.java)
        val info = project.tasks.named("trickuildInfo", TrickuildInfoTask::class.java).get()
        val metadata = project.tasks.named("generateTrickuildMetadata", GenerateTrickuildMetadataTask::class.java).get()
        fun assertEffective(expected: String) {
            assertEquals(expected, info.codename.get())
            assertEquals(expected, metadata.codename.get())
        }

        assertEquals("Youngchun", extension.fallbackCodename.get())
        assertFalse(extension.codename.isPresent)
        assertEffective("Youngchun")
        assertFalse(extension.codename.isPresent)

        extension.codename.set(" ")
        extension.fallbackCodename.set("Unnamed")
        assertEffective("Unnamed")
        assertEquals(" ", extension.codename.get())
        assertEquals("Unnamed", extension.fallbackCodename.get())

        extension.codename.set("Epica")
        assertEffective("Epica")
        extension.codename.set(null as String?)
        assertEffective("Unnamed")
        extension.fallbackCodename.set(null as String?)
        assertEffective("Youngchun")
        assertFalse(extension.codename.isPresent)

        // A value-less Provider overrides a Property convention; resolution still has a default.
        extension.codename.set(project.providers.gradleProperty("missingCodename"))
        extension.fallbackCodename.set(project.providers.gradleProperty("missingFallbackCodename"))
        assertEffective("Youngchun")
        assertFalse(extension.codename.isPresent)
        assertFalse(extension.fallbackCodename.isPresent)
    }
}
