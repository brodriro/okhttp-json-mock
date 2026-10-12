package io.github.brodriro.okhttpjsonmock

import java.io.IOException
import kotlin.test.Test
import kotlin.test.assertContains
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNull

class MockMetaTest {

    @Test
    fun `status and headers are read`() {
        val meta = MockMeta.parse("status: 201\nLocation: /api/v1/users/4\nX-Total-Count: 3", "users.post.meta")

        assertEquals(201, meta.status)
        assertEquals(listOf("Location" to "/api/v1/users/4", "X-Total-Count" to "3"), meta.headers)
        assertNull(meta.contentType)
    }

    @Test
    fun `status defaults to 200 when the file only has headers`() {
        val meta = MockMeta.parse("X-Total-Count: 3", "users.meta")

        assertEquals(200, meta.status)
    }

    @Test
    fun `blank lines, comments, windows line endings and a byte order mark are ignored`() {
        val meta = MockMeta.parse("﻿# created\r\n\r\nstatus: 201\r\n  # indented comment\r\n", "users.post.meta")

        assertEquals(201, meta.status)
        assertEquals(emptyList(), meta.headers)
    }

    @Test
    fun `status key is case insensitive and spaces around the colon are optional`() {
        assertEquals(204, MockMeta.parse("Status:204", "a.meta").status)
        assertEquals(204, MockMeta.parse("STATUS  :  204", "a.meta").status)
    }

    @Test
    fun `header value keeps the colons after the first one`() {
        val meta = MockMeta.parse("Location: https://example.com:8443/users/4", "a.meta")

        assertEquals(listOf("Location" to "https://example.com:8443/users/4"), meta.headers)
    }

    @Test
    fun `content type is kept apart from the other headers`() {
        val meta = MockMeta.parse("content-type: text/plain\nX-Trace: 1", "a.meta")

        assertEquals("text/plain", meta.contentType)
        assertEquals(listOf("X-Trace" to "1"), meta.headers)
    }

    @Test
    fun `line without a colon is rejected with file and line number`() {
        val error = assertFailsWith<IOException> { MockMeta.parse("status: 201\nLocation", "users.post.meta") }

        assertContains(error.message!!, "users.post.meta:2")
    }

    @Test
    fun `status that is not an http code is rejected`() {
        assertFailsWith<IOException> { MockMeta.parse("status: created", "a.meta") }
        assertFailsWith<IOException> { MockMeta.parse("status: 99", "a.meta") }
        assertFailsWith<IOException> { MockMeta.parse("status: 600", "a.meta") }
    }

    @Test
    fun `header name that okhttp would refuse is rejected`() {
        val error = assertFailsWith<IOException> { MockMeta.parse("Bad Header: 1", "a.meta") }

        assertContains(error.message!!, "a.meta:1")
    }
}
