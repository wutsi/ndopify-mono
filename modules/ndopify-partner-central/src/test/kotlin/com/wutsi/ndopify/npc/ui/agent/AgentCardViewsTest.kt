package com.wutsi.ndopify.npc.ui.agent

import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class AgentCardViewsTest {
    @Test
    fun `builds the card from the agent and the back link`() {
        val card = AgentCardViews.of(AgentProfileFixtures.AGENT, "/mandates/204#matches-title")

        assertEquals(30L, card.id)
        assertEquals("Clarisse Mbarga", card.name)
        assertEquals("CM", card.initials)
        assertEquals("https://cdn.ndopify.com/party/300/photo/a.jpg", card.photoUrl)
        assertEquals("https://wa.me/237699123456", card.whatsappUrl)
        assertEquals("/agent?id=30&back=/mandates/204%23matches-title", card.profileUrl)
    }

    @Test
    fun `an agent without photo or WhatsApp leaves both null`() {
        val card = AgentCardViews.of(AgentProfileFixtures.SPARSE_AGENT, "/visits/7001")

        assertNull(card.photoUrl)
        assertNull(card.whatsappUrl)
    }
}
