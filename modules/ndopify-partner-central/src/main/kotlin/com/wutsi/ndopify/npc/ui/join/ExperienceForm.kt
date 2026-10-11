package com.wutsi.ndopify.npc.ui.join

import com.wutsi.ndopify.agent.dto.AgentType
import com.wutsi.ndopify.agent.dto.ExperienceLevel

/** Step "experience". */
data class ExperienceForm(
    val experienceLevel: ExperienceLevel? = null,
    val agentType: AgentType? = null,
)
