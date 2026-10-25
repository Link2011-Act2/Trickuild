package dev.trickuild.gradle

import org.gradle.testfixtures.ProjectBuilder
import kotlin.test.Test
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
}
