package com.wutsi.ndopify.npc.ui.agent

import com.wutsi.ndopify.agent.dto.Agent
import com.wutsi.ndopify.npc.ui.kyc.KycRules
import com.wutsi.ndopify.npc.ui.profile.ProfileRules

/** Builds the one standard card that names another agent, everywhere Partner Central shows one. */
object AgentCardViews {
    /**
     * [agent] as a card whose profile link returns to [back]. Only its identity is carried: never its customer,
     * mandate, match, code or report (anti-bypass).
     */
    fun of(agent: Agent, back: String): AgentCardView =
        AgentCardView(
            id = agent.id,
            name = ProfileRules.fullName(agent),
            initials = KycRules.initials(agent.party.firstName, agent.party.lastName),
            photoUrl = agent.party.photoUrl?.ifBlank { null },
            whatsappUrl = AgentProfileRules.whatsappUrl(agent.whatsappNumber),
            profileUrl = AgentProfileRules.profileUrl(agent.id, back),
        )
}
