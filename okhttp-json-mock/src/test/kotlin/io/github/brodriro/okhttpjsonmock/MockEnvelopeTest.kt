package io.github.brodriro.okhttpjsonmock

import java.io.IOException
import kotlin.test.Test
import kotlin.test.assertContains
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNotNull
import kotlin.test.assertNull

class MockEnvelopeTest {

    private fun parse(json: String) = MockEnvelope.parseOrNull(json.toByteArray(), "users.post.json")

    @Test
    fun `status code and response are read`() {
        val envelope = assertNotNull(parse("""{ "statusCode": 201, "response": { "id": 4, "name": "Ana" } }"""))

        assertEquals(201, envelope.statusCode)
        assertEquals("""{"id":4,"name":"Ana"}""", envelope.body.decodeToString())
    }

    @Test
    fun `response can be an array, a string or null`() {
        assertEquals("[1,2]", parse("""{"statusCode":200,"response":[1,2]}""")!!.body.decodeToString())
        assertEquals("\"ok\"", parse("""{"statusCode":200,"response":"ok"}""")!!.body.decodeToString())
        assertEquals("null", parse("""{"statusCode":200,"response":null}""")!!.body.decodeToString())
    }

    @Test
    fun `envelope without response has an empty body`() {
        val envelope = assertNotNull(parse("""{ "statusCode": 204 }"""))

        assertEquals(204, envelope.statusCode)
        assertEquals(0, envelope.body.size)
    }

    @Test
    fun `response content is not altered beyond whitespace`() {
        val envelope = parse(
            """{"statusCode":200,"response":{"a":null,"url":"https://x.test/?a=1&b=<2>","big":12345678901234567890,"n":1.50,"ñ":"日本"}}""",
        )!!

        assertEquals(
            """{"a":null,"url":"https://x.test/?a=1&b=<2>","big":12345678901234567890,"n":1.50,"ñ":"日本"}""",
            envelope.body.decodeToString(),
        )
    }

    @Test
    fun `plain mocks are not envelopes`() {
        assertNull(parse("""{"id":1,"name":"John"}"""))
        assertNull(parse("""[{"id":1},{"id":2}]"""))
        assertNull(parse("""{"response":{"id":1}}"""))
    }

    @Test
    fun `payload with a statusCode field and other keys is a plain mock`() {
        assertNull(parse("""{"statusCode":404,"message":"Not found","timestamp":1700000000}"""))
        assertNull(parse("""[{"statusCode":201}]"""))
    }

    @Test
    fun `file that is not valid json is a plain mock`() {
        assertNull(parse("""{"statusCode": 201, "response": """))
        assertNull(parse("statusCode is mentioned in plain text"))
    }

    @Test
    fun `status code that is not an http code is rejected naming the file`() {
        val error = assertFailsWith<IOException> { parse("""{"statusCode":"created"}""") }

        assertContains(error.message!!, "users.post.json")
        assertFailsWith<IOException> { parse("""{"statusCode":99}""") }
        assertFailsWith<IOException> { parse("""{"statusCode":600,"response":{}}""") }
        assertFailsWith<IOException> { parse("""{"statusCode":201.5}""") }
        assertFailsWith<IOException> { parse("""{"statusCode":null}""") }
    }
}
