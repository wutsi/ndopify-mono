package com.wutsi.ndopify.agent.server.mail

import com.wutsi.ndopify.agent.server.domain.AgentEntity
import com.wutsi.ndopify.party.server.domain.PartyEntity
import com.wutsi.ndopify.refdata.server.domain.TenantEntity
import com.wutsi.ndopify.refdata.server.service.TenantFormatter
import java.net.URLDecoder
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class AgentMailContextTest {
    private val formatter = TenantFormatter(TenantEntity(id = 1L, name = "Ndopify"))

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

    @Test
    fun `the footer links the agent's WhatsApp number`() {
        assertEquals(
            "https://wa.me/237699000001",
            AgentMailContext.of(agent("+237 6 99 00 00 01"), formatter)["agent_whatsapp_url"],
        )
        assertEquals(
            "https://wa.me/237699000001",
            AgentMailContext.of(agent("237699000001"), formatter)["agent_whatsapp_url"],
        )
    }

    @Test
    fun `no footer WhatsApp URL without a usable number`() {
        assertNull(AgentMailContext.of(agent(null), formatter)["agent_whatsapp_url"])
        assertNull(AgentMailContext.of(agent(" "), formatter)["agent_whatsapp_url"])
        assertNull(AgentMailContext.of(agent("n/a"), formatter)["agent_whatsapp_url"])
    }
}
