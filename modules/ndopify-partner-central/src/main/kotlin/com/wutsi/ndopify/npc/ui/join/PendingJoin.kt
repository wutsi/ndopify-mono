package com.wutsi.ndopify.npc.ui.join

import com.wutsi.ndopify.agent.dto.AgentType
import com.wutsi.ndopify.agent.dto.CreateAgentRequest
import com.wutsi.ndopify.agent.dto.ExperienceLevel
import java.io.Serializable

/**
 * The answers of a "Rejoindre Ndopify" application in progress, kept in the HTTP session from the first step to the
 * submission. Answers are only stored once their step validated them, so the session is the trusted copy: the review
 * step submits it as is, never values from the page.
 */
data class PendingJoin(
    val firstName: String? = null,
    val lastName: String? = null,
    val email: String? = null,
    val whatsappNumber: String? = null,
    val experienceLevel: ExperienceLevel? = null,
    val agentType: AgentType? = null,
    val cityId: Long? = null,
    val neighborhoodIds: List<Long> = emptyList(),
    val mobileMoneyNumber: String? = null,
) : Serializable {
    companion object {
        const val SESSION_ATTRIBUTE = "npc.pending-join"
    }

    fun isComplete(step: String): Boolean = when (step) {
        JoinController.STEP_IDENTITY -> listOf(
            JoinRules.firstName(firstName),
            JoinRules.lastName(lastName),
            JoinRules.email(email),
            JoinRules.whatsappNumber(whatsappNumber),
        ).all { it == null }

        JoinController.STEP_EXPERIENCE ->
            JoinRules.experienceLevel(experienceLevel) == null && JoinRules.agentType(agentType) == null

        JoinController.STEP_AREA ->
            JoinRules.cityId(cityId) == null && JoinRules.neighborhoodIds(neighborhoodIds) == null

        JoinController.STEP_PAYMENT -> JoinRules.mobileMoneyNumber(mobileMoneyNumber) == null

        else -> false
    }

    /** The first step that still needs answers, or the review step when they're all done. */
    fun firstIncompleteStep(): String =
        JoinController.STEPS.dropLast(1).firstOrNull { step -> !isComplete(step) } ?: JoinController.STEP_REVIEW

    fun toCreateAgentRequest() = CreateAgentRequest(
        firstName = firstName.orEmpty(),
        lastName = lastName.orEmpty(),
        email = email.orEmpty(),
        whatsappNumber = whatsappNumber.orEmpty(),
        mobileMoneyNumber = mobileMoneyNumber.orEmpty(),
        agentType = agentType ?: AgentType.UNKNOWN,
        experienceLevel = experienceLevel ?: ExperienceLevel.UNKNOWN,
        cityId = cityId ?: -1,
        neighborhoodIds = neighborhoodIds,
    )
}
