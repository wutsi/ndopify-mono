package com.wutsi.ndopify.npc.ui.profile

import com.wutsi.ndopify.agent.dto.Agent
import com.wutsi.ndopify.agent.dto.AgentType
import com.wutsi.ndopify.agent.dto.ExperienceLevel
import com.wutsi.ndopify.npc.ui.profile.ProfileFixtures.AGENT
import com.wutsi.ndopify.refdata.dto.Location
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class ProfileRulesTest {
    private val douala = Location(id = 1, name = "Douala")
    private val akwa = Location(id = 2, name = "Akwa")
    private val bonapriso = Location(id = 3, name = "Bonapriso")

    @Test
    fun biography() {
        assertNull(ProfileRules.biography(""))
        assertNull(ProfileRules.biography("x".repeat(500)))
        assertTrue(ProfileRules.biography("x".repeat(501))!!.contains("500"))
    }

    @Test
    fun area() {
        assertEquals("Akwa, Bonapriso — Douala", ProfileRules.area(douala, listOf(akwa, bonapriso)))
        assertEquals("Douala", ProfileRules.area(douala, emptyList()))
        assertNull(ProfileRules.area(null, listOf(akwa)))
    }

    @Test
    fun `form from the agent`() {
        assertEquals(
            ProfileForm(
                whatsappNumber = "+237670000001",
                experienceLevel = ExperienceLevel.SENIOR,
                agentType = AgentType.INDEPENDANT,
                cityId = ProfileFixtures.DOUALA,
                neighborhoodIds = listOf(ProfileFixtures.BONAPRISO, ProfileFixtures.AKWA),
                biography = "Démarcheur à Douala depuis 6 ans.",
                version = 4,
            ),
            ProfileForm.of(AGENT.copy(version = 4)),
        )
        // Unknown answers show as unanswered.
        assertEquals(ProfileForm(version = 0), ProfileForm.of(Agent()))
    }
}
