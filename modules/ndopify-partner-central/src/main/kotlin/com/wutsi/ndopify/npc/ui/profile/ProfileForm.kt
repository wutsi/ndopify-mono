package com.wutsi.ndopify.npc.ui.profile

import com.wutsi.ndopify.agent.dto.Agent
import com.wutsi.ndopify.agent.dto.AgentType
import com.wutsi.ndopify.agent.dto.ExperienceLevel

/** `POST /profile`. The WhatsApp number is E.164, filled in by intl-tel-input on the page. */
data class ProfileForm(
    val whatsappNumber: String = "",
    val experienceLevel: ExperienceLevel? = null,
    val agentType: AgentType? = null,
    val cityId: Long? = null,
    val neighborhoodIds: List<Long> = emptyList(),
    val biography: String = "",
) {
    companion object {
        /** The form pre-filled from the agent's profile; an UNKNOWN answer shows as unanswered. */
        fun of(agent: Agent) = ProfileForm(
            whatsappNumber = agent.whatsappNumber.orEmpty(),
            experienceLevel = agent.experienceLevel.takeIf { it != ExperienceLevel.UNKNOWN },
            agentType = agent.agentType.takeIf { it != AgentType.UNKNOWN },
            cityId = agent.cityId,
            neighborhoodIds = agent.neighborhoodIds,
            biography = agent.biography.orEmpty(),
        )
    }
}
