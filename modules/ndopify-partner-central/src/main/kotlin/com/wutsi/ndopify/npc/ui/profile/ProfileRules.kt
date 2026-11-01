package com.wutsi.ndopify.npc.ui.profile

import com.wutsi.ndopify.agent.dto.Agent
import com.wutsi.ndopify.npc.ui.kyc.KycRules
import org.springframework.ui.Model

/** Rules of the profile pages and of the Espace agent's profile section, kept free of HTTP. */
object ProfileRules {
    const val MAX_BIOGRAPHY = 500

    /** Flash attribute the profile pages leave for the next page's success message. */
    const val SAVED = "saved"
    const val SAVED_PROFILE = "profile"
    const val SAVED_PHOTO = "photo"

    fun biography(value: String): String? =
        if (value.length > MAX_BIOGRAPHY) "Votre biographie dépasse $MAX_BIOGRAPHY caractères. Raccourcissez-la." else null

    /** Where the agent goes after saving, whatever its KYC status: the Espace agent. */
    const val HOME = "redirect:/"

    fun fullName(agent: Agent): String = "${agent.party.firstName} ${agent.party.lastName}".trim()

    /** The header's account menu: the agent's photo, or its initials without one. */
    fun header(agent: Agent, model: Model) {
        model.addAttribute("userPhotoUrl", agent.party.photoUrl?.ifBlank { null })
        model.addAttribute("initials", KycRules.initials(agent.party.firstName, agent.party.lastName))
    }
}
