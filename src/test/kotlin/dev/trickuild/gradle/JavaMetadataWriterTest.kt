package dev.trickuild.gradle

import kotlin.test.Test
import kotlin.test.assertContains
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class JavaMetadataWriterTest {
    @Test
    fun `quotes and backslashes cannot escape generated Java literals`() {
        assertEquals("\"a\\\"b\\\\c\\n\\t\"", JavaMetadataWriter.literal("a\"b\\c\n\t"))
        val java = JavaMetadataWriter.render("example.generated", "Metadata", mapOf("CODE_NAME" to "エピカ\"\\"), null)
        assertContains(java, "public static final String CODE_NAME = \"エピカ\\\"\\\\\";")
        assertFailsWith<IllegalArgumentException> { JavaMetadataWriter.render("example; injected", "Metadata", emptyMap(), null) }
        assertFailsWith<IllegalArgumentException> { JavaMetadataWriter.render("example", "../Metadata", emptyMap(), null) }
        assertFailsWith<IllegalArgumentException> { JavaMetadataWriter.render("example", "class", emptyMap(), null) }
    }
}
