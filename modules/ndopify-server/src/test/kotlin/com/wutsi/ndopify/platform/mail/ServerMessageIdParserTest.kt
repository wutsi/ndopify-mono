package com.wutsi.ndopify.platform.mail

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class ServerMessageIdParserTest {
    private val usEast1 = "email-smtp.us-east-1.amazonaws.com"
    private val euWest2 = "email-smtp.eu-west-2.amazonaws.com"

    @Test
    fun `a bare local part gets the us-east-1 domain`() {
        assertEquals(
            "01010160d7de98d8-21e57d9a-JZho-416c-bbe1-8ebaAexample-000000@email.amazonses.com",
            ServerMessageIdParser.parse(
                "250 Ok 01010160d7de98d8-21e57d9a-JZho-416c-bbe1-8ebaAexample-000000",
                usEast1,
            ),
        )
    }

    @Test
    fun `another region gets its own domain`() {
        assertEquals(
            "01010160d7de98d8-21e57d9a-JZho-416c-bbe1-8ebaAexample-000000@eu-west-2.amazonses.com",
            ServerMessageIdParser.parse(
                "250 Ok 01010160d7de98d8-21e57d9a-JZho-416c-bbe1-8ebaAexample-000000",
                euWest2,
            ),
        )
    }

    @Test
    fun `a reply that already names a domain is kept`() {
        assertEquals(
            "0101-abc@email.amazonses.com",
            ServerMessageIdParser.parse("250 Ok 0101-abc@email.amazonses.com", usEast1),
        )
    }

    @Test
    fun `angle brackets are stripped`() {
        assertEquals(
            "0101-abc@email.amazonses.com",
            ServerMessageIdParser.parse("250 Ok <0101-abc@email.amazonses.com>", usEast1),
        )
        assertEquals(
            "0101-abc@email.amazonses.com",
            ServerMessageIdParser.parse("250 Ok <0101-abc>", usEast1),
        )
    }

    @Test
    fun `a relay that names nothing captures nothing`() {
        assertNull(ServerMessageIdParser.parse("250 OK", usEast1))
        assertNull(ServerMessageIdParser.parse("250 2.0.0 Ok: queued as 4Wxyz", usEast1))
        assertNull(ServerMessageIdParser.parse(null, usEast1))
        assertNull(ServerMessageIdParser.parse("garbage", usEast1))
    }

    @Test
    fun `a non-SES host never captures`() {
        assertNull(ServerMessageIdParser.parse("250 Ok 0101-abc", "localhost"))
        assertNull(ServerMessageIdParser.parse("250 Ok 0101-abc", "smtp.example.com"))
        assertNull(ServerMessageIdParser.parse("250 Ok 0101-abc", null))
    }
}
