package io.github.brodriro.okhttpjsonmock

import io.github.brodriro.okhttpjsonmock.providers.InputStreamProvider
import okhttp3.OkHttpClient
import okhttp3.Request
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals

class ReasonPhrasesTest {

    @Test
    fun `registered codes get their RFC 9110 name`() {
        assertEquals("Non-Authoritative Information", ReasonPhrases.of(203))
        assertEquals("Moved Permanently", ReasonPhrases.of(301))
        assertEquals("Permanent Redirect", ReasonPhrases.of(308))
        assertEquals("Method Not Allowed", ReasonPhrases.of(405))
        assertEquals("Content Too Large", ReasonPhrases.of(413))
        assertEquals("Unprocessable Content", ReasonPhrases.of(422))
        assertEquals("Unavailable For Legal Reasons", ReasonPhrases.of(451))
        assertEquals("Not Implemented", ReasonPhrases.of(501))
        assertEquals("Network Authentication Required", ReasonPhrases.of(511))
    }

    @Test
    fun `418 keeps its well-known name`() {
        assertEquals("I'm a teapot", ReasonPhrases.of(418))
    }

    @Test
    fun `unregistered codes get the name of their class`() {
        assertEquals("Informational", ReasonPhrases.of(199))
        assertEquals("Successful", ReasonPhrases.of(299))
        assertEquals("Redirection", ReasonPhrases.of(306))
        assertEquals("Client Error", ReasonPhrases.of(499))
        assertEquals("Server Error", ReasonPhrases.of(599))
    }

    @Test
    fun `every code an envelope accepts has a specific phrase`() {
        (100..599).forEach { code -> assertNotEquals("Mock Response", ReasonPhrases.of(code), "code $code") }
    }

    @Test
    fun `envelope status reaches the status line with its phrase`() {
        val mock = """{ "statusCode": 405 }"""
        val provider = InputStreamProvider { path -> mock.byteInputStream().takeIf { path == "api/v1/users.json" } }
        val interceptor = OkHttpMockInterceptor.Builder(provider).noDelay().logger(MockLogger.NONE).build()
        val client = OkHttpClient.Builder().addInterceptor(interceptor).build()

        val response = client.newCall(Request.Builder().url("http://example.com/api/v1/users").build()).execute()

        assertEquals(405, response.code)
        assertEquals("Method Not Allowed", response.message)
    }
}
