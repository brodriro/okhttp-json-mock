package io.github.brodriro.okhttpjsonmock.providers

import java.io.ByteArrayOutputStream
import java.io.File
import java.io.PrintStream
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class DefaultInputStreamProviderTest {

    @Test
    fun `existing file is opened`() {
        val file = File.createTempFile("mock", ".json").apply {
            deleteOnExit()
            writeText("""{"id":1}""")
        }

        val content = DefaultInputStreamProvider().provide(file.path)!!.use { it.readBytes().decodeToString() }

        assertEquals("""{"id":1}""", content)
    }

    @Test
    fun `missing file returns null without printing a stack trace`() {
        val originalErr = System.err
        val captured = ByteArrayOutputStream()
        System.setErr(PrintStream(captured))
        try {
            assertNull(DefaultInputStreamProvider().provide("does/not/exist.json"))
        } finally {
            System.setErr(originalErr)
        }

        assertEquals("", captured.toString())
    }

    @Test
    fun `directory is not treated as a mock file`() {
        assertNull(DefaultInputStreamProvider().provide(System.getProperty("java.io.tmpdir")))
    }
}
