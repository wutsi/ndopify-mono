package com.wutsi.ndopify.agent.server.mail

import com.wutsi.ndopify.agent.server.domain.AgentEntity
import com.wutsi.ndopify.party.server.domain.PartyEntity
import java.net.URLDecoder
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class AgentMailContextTest {
    private fun agent(number: String?) = AgentEntity(
        id = 20L,
        party = PartyEntity(id = 200L, firstName = "Kim", lastName = "Possible"),
        whatsappNumber = number,
    )

    @Test
    fun `reply URL carries the pre-filled context`() {
        val url = AgentMailContext.replyUrl(agent("+237 6 99 00 00 01"), "de votre mandat à Bonapriso, Douala")

        assertEquals("https://wa.me/237699000001?text=", url!!.substringBefore("Bonjour"))
        assertEquals(
            "Bonjour, je vous réponds au sujet de votre mandat à Bonapriso, Douala.",
            URLDecoder.decode(url.substringAfter("?text="), "UTF-8"),
        )
    }

    @Test
    fun `no reply URL without a usable number`() {
        assertNull(AgentMailContext.replyUrl(agent(null), "de votre mandat"))
        assertNull(AgentMailContext.replyUrl(agent(" "), "de votre mandat"))
        assertNull(AgentMailContext.replyUrl(agent("n/a"), "de votre mandat"))
    }
}
